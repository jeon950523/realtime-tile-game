package com.realtimetilegame.game.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GamePublicState;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.game.application.dto.GameStartedPayload;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.game.domain.state.InitialTileDistribution;
import com.realtimetilegame.game.domain.state.InitialTileDistributor;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.tile.TileSetFactory;
import com.realtimetilegame.game.event.GameStartedCommittedEvent;
import com.realtimetilegame.room.application.RoomQueryService;
import com.realtimetilegame.room.application.dto.RoomStartEligibility;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomGameMode;
import com.realtimetilegame.room.domain.RoomPlayer;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class GameStartService {
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final GameTileRepository gameTileRepository;
    private final InitialTileDistributor initialTileDistributor;
    private final GameStartRandomizer randomizer;
    private final GameStateAssembler stateAssembler;
    private final GameEventPublisher eventPublisher;
    private final Clock clock;

    public GameStartService(UserRepository userRepository, RoomRepository roomRepository,
                            RoomPlayerRepository roomPlayerRepository, GameRepository gameRepository,
                            GamePlayerRepository gamePlayerRepository, GameTileRepository gameTileRepository,
                            InitialTileDistributor initialTileDistributor, GameStartRandomizer randomizer,
                            GameStateAssembler stateAssembler, GameEventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameTileRepository = gameTileRepository;
        this.initialTileDistributor = initialTileDistributor;
        this.randomizer = randomizer;
        this.stateAssembler = stateAssembler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GameStartResult startGame(long roomId, long requesterUserId) {
        requireLockedActiveUser(requesterUserId);
        Room room = requireLockedWaitingRoom(roomId);
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findActiveByRoomIdForUpdate(roomId).stream()
            .sorted(Comparator.comparingInt(RoomPlayer::seatOrder))
            .toList();
        RoomPlayer requester = roomPlayers.stream()
            .filter(player -> player.user().id() == requesterUserId)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED));
        if (!requester.owner() || room.owner().id() != requesterUserId) {
            throw new BusinessException(ErrorCode.ROOM_OWNER_REQUIRED);
        }
        requireStartable(room, roomPlayers);
        if (gameRepository.existsByRoomId(roomId)) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_PLAYING);
        }

        int firstIndex = randomizer.firstPlayerIndex(roomPlayers.size());
        if (firstIndex < 0 || firstIndex >= roomPlayers.size()) {
            throw new IllegalStateException("randomizer returned an invalid first player index");
        }
        RoomPlayer firstRoomPlayer = roomPlayers.get(firstIndex);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Game game = gameRepository.saveAndFlush(Game.startClassic(
            room, firstRoomPlayer.user(), firstRoomPlayer.seatOrder(), now
        ));

        List<GamePlayer> gamePlayers = gamePlayerRepository.saveAllAndFlush(
            roomPlayers.stream()
                .map(player -> GamePlayer.snapshot(game, player.user(), player.seatOrder(), now))
                .toList()
        ).stream().sorted(Comparator.comparingInt(GamePlayer::seatOrder)).toList();

        List<Tile> standardTiles = TileSetFactory.createStandardSet();
        List<Tile> shuffledTiles = requireStandardShuffle(
            standardTiles,
            randomizer.shuffledTiles(standardTiles)
        );
        InitialTileDistribution distribution = initialTileDistributor.distribute(
            shuffledTiles,
            gamePlayers.stream().map(player -> participantId(player.user().id())).toList()
        );
        List<GameTile> gameTiles = gameTileRepository.saveAllAndFlush(
            createGameTiles(game, gamePlayers, distribution, now)
        );

        room.startGame(now);
        roomRepository.saveAndFlush(room);
        verifyPersistedState(game, gamePlayers, gameTiles);

        GamePublicState publicState = stateAssembler.publicState(game, gamePlayers, gameTiles);
        Map<Long, GamePrivateState> privateStates = stateAssembler.privateStates(game, gamePlayers, gameTiles);
        OffsetDateTime occurredAt = OffsetDateTime.of(now, ZoneOffset.UTC);
        eventPublisher.publish(new GameStartedCommittedEvent(
            game.id(),
            room.id(),
            occurredAt,
            new GameStartedPayload(game.id(), room.id(), "/games/" + game.id(), publicState),
            privateStates
        ));

        return new GameStartResult(
            game.id(), room.id(), game.status().name(), game.currentTurnUser().id(),
            game.currentTurnSeatOrder(), game.turnNumber(), gamePlayers.size()
        );
    }

    private User requireLockedActiveUser(long userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }

    private Room requireLockedWaitingRoom(long roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.status() == RoomStatus.CLOSED) throw new BusinessException(ErrorCode.ROOM_CLOSED);
        if (room.status() != RoomStatus.WAITING) throw new BusinessException(ErrorCode.ROOM_ALREADY_PLAYING);
        if (room.gameMode() != RoomGameMode.CLASSIC) throw new BusinessException(ErrorCode.INVALID_GAME_MODE);
        return room;
    }

    private static void requireStartable(Room room, List<RoomPlayer> players) {
        RoomStartEligibility eligibility = RoomQueryService.eligibility(room, players);
        if (eligibility.startable()) return;
        if ("ROOM_MIN_PLAYERS_NOT_MET".equals(eligibility.blockReason())) {
            throw new BusinessException(ErrorCode.ROOM_MIN_PLAYERS_NOT_MET);
        }
        if ("ROOM_PLAYERS_NOT_READY".equals(eligibility.blockReason())) {
            throw new BusinessException(ErrorCode.ROOM_PLAYERS_NOT_READY);
        }
        if ("ROOM_FULL".equals(eligibility.blockReason())) {
            throw new BusinessException(ErrorCode.ROOM_FULL);
        }
        throw new BusinessException(ErrorCode.ROOM_ALREADY_PLAYING);
    }


    private static List<Tile> requireStandardShuffle(List<Tile> standardTiles, List<Tile> shuffledTiles) {
        if (shuffledTiles == null || shuffledTiles.size() != TileSetFactory.STANDARD_TILE_COUNT) {
            throw new IllegalStateException("randomizer must return exactly 106 tiles");
        }
        Set<TileId> expected = standardTiles.stream().map(Tile::id).collect(java.util.stream.Collectors.toSet());
        Set<TileId> actual = shuffledTiles.stream()
            .map(tile -> {
                if (tile == null) throw new IllegalStateException("randomizer must not return null tiles");
                return tile.id();
            })
            .collect(java.util.stream.Collectors.toSet());
        if (actual.size() != TileSetFactory.STANDARD_TILE_COUNT || !actual.equals(expected)) {
            throw new IllegalStateException("randomizer must return a permutation of the standard tile set");
        }
        return List.copyOf(shuffledTiles);
    }

    private static List<GameTile> createGameTiles(Game game, List<GamePlayer> players,
                                                   InitialTileDistribution distribution, LocalDateTime now) {
        Map<ParticipantId, GamePlayer> byParticipant = new HashMap<>();
        players.forEach(player -> byParticipant.put(participantId(player.user().id()), player));
        List<GameTile> tiles = new ArrayList<>(TileSetFactory.STANDARD_TILE_COUNT);
        distribution.racks().forEach((participantId, rack) -> {
            GamePlayer owner = byParticipant.get(participantId);
            if (owner == null) throw new IllegalStateException("distribution contains an unknown participant");
            List<TileId> tileIds = rack.tileIds();
            for (int position = 0; position < tileIds.size(); position++) {
                tiles.add(GameTile.rack(game, owner, tileIds.get(position), position, now));
            }
        });
        List<TileId> pool = distribution.tilePool().remainingTileIds();
        for (int position = 0; position < pool.size(); position++) {
            tiles.add(GameTile.pool(game, pool.get(position), position, now));
        }
        return List.copyOf(tiles);
    }

    private void verifyPersistedState(Game game, List<GamePlayer> players, List<GameTile> tiles) {
        int expectedPool = TileSetFactory.STANDARD_TILE_COUNT
            - players.size() * InitialTileDistributor.INITIAL_RACK_SIZE;
        if (gameRepository.countByRoomId(game.room().id()) != 1L
            || gamePlayerRepository.countByGameId(game.id()) != players.size()
            || gameTileRepository.countByGameId(game.id()) != TileSetFactory.STANDARD_TILE_COUNT
            || gameTileRepository.countByGameIdAndLocation(game.id(), GameTileLocation.POOL) != expectedPool) {
            throw new IllegalStateException("persisted initial game state violates invariants");
        }
        if (new HashSet<>(tiles.stream().map(tile -> tile.tileId().value()).toList()).size()
            != TileSetFactory.STANDARD_TILE_COUNT) {
            throw new IllegalStateException("persisted initial game state contains duplicated tiles");
        }
        Map<Long, Long> rackCounts = tiles.stream()
            .filter(tile -> tile.location() == GameTileLocation.RACK)
            .collect(java.util.stream.Collectors.groupingBy(tile -> tile.owner().id(), java.util.stream.Collectors.counting()));
        if (players.stream().anyMatch(player ->
            rackCounts.getOrDefault(player.id(), 0L) != InitialTileDistributor.INITIAL_RACK_SIZE)) {
            throw new IllegalStateException("every player must have exactly 14 tiles");
        }
    }

    private static ParticipantId participantId(long userId) {
        return new ParticipantId(Long.toString(userId));
    }
}
