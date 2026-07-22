package com.realtimetilegame.game.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.GamePlayerPublicView;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GamePublicState;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.TileDrawnPayload;
import com.realtimetilegame.game.application.dto.TurnPassedPayload;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeldRepository;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class GameTurnCommandService {
    public static final String DRAW = "DRAW";
    public static final String PASS = "PASS";

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final GameTileRepository gameTileRepository;
    private final GameMeldRepository gameMeldRepository;
    private final GameStateAssembler stateAssembler;
    private final GameEventPublisher eventPublisher;
    private final Clock clock;

    public GameTurnCommandService(UserRepository userRepository, GameRepository gameRepository,
                                  GamePlayerRepository gamePlayerRepository,
                                  GameTileRepository gameTileRepository,
                                  GameMeldRepository gameMeldRepository,
                                  GameStateAssembler stateAssembler,
                                  GameEventPublisher eventPublisher,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameTileRepository = gameTileRepository;
        this.gameMeldRepository = gameMeldRepository;
        this.stateAssembler = stateAssembler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GameTurnCommandResult draw(long gameId, long requesterUserId, long expectedGameVersion) {
        requireActiveUser(requesterUserId);
        Game game = requireLockedGame(gameId);
        requireInProgress(game);
        List<GamePlayer> players = orderedPlayers(gameId);
        GamePlayer requester = requireMembership(players, requesterUserId);
        requireExpectedVersion(game, expectedGameVersion);
        requireCurrentTurn(game, requesterUserId);

        GameTile poolTile = gameTileRepository.findFirstPoolTileForUpdate(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DRAW_POOL_EMPTY));
        int rackPosition = gameTileRepository.findNextRackPosition(requester.id());
        GamePlayer nextPlayer = nextPlayer(players, game.currentTurnSeatOrder());
        LocalDateTime now = now();

        poolTile.drawTo(requester, rackPosition, now);
        game.advanceAfterDraw(
            nextPlayer.user(),
            nextPlayer.seatOrder(),
            UUID.randomUUID().toString(),
            now,
            game.room().turnTimeLimitSeconds()
        );
        gameRepository.saveAndFlush(game);

        StateBundle state = loadState(game, players);
        int drawnByRackCount = state.publicState().players().stream()
            .filter(player -> player.userId() == requesterUserId)
            .findFirst()
            .map(GamePlayerPublicView::rackTileCount)
            .orElseThrow(() -> new IllegalStateException("draw requester missing from public state"));
        OffsetDateTime occurredAt = OffsetDateTime.of(now, ZoneOffset.UTC);
        eventPublisher.publish(new GameTurnCommittedEvent(
            game.id(),
            "TILE_DRAWN",
            occurredAt,
            new TileDrawnPayload(
                game.id(),
                game.version(),
                requesterUserId,
                drawnByRackCount,
                state.publicState().tilePoolCount(),
                game.currentTurnUser().id(),
                game.currentTurnSeatOrder(),
                game.turnNumber(),
                game.currentTurnId(),
                OffsetDateTime.of(game.currentTurnStartedAt(), ZoneOffset.UTC),
                OffsetDateTime.of(game.currentTurnDeadlineAt(), ZoneOffset.UTC),
                game.consecutivePassCount()
            ),
            state.privateStates()
        ));
        return new GameTurnCommandResult(game.id(), DRAW, game.version());
    }

    @Transactional
    public GameTurnCommandResult pass(long gameId, long requesterUserId, long expectedGameVersion) {
        requireActiveUser(requesterUserId);
        Game game = requireLockedGame(gameId);
        requireInProgress(game);
        List<GamePlayer> players = orderedPlayers(gameId);
        requireMembership(players, requesterUserId);
        requireExpectedVersion(game, expectedGameVersion);
        requireCurrentTurn(game, requesterUserId);
        if (gameTileRepository.countByGameIdAndLocation(gameId, GameTileLocation.POOL) > 0) {
            throw new BusinessException(ErrorCode.PASS_NOT_ALLOWED);
        }

        GamePlayer nextPlayer = nextPlayer(players, game.currentTurnSeatOrder());
        LocalDateTime now = now();
        game.advanceAfterPass(
            nextPlayer.user(),
            nextPlayer.seatOrder(),
            UUID.randomUUID().toString(),
            now,
            game.room().turnTimeLimitSeconds()
        );
        gameRepository.saveAndFlush(game);

        StateBundle state = loadState(game, players);
        OffsetDateTime occurredAt = OffsetDateTime.of(now, ZoneOffset.UTC);
        eventPublisher.publish(new GameTurnCommittedEvent(
            game.id(),
            "TURN_PASSED",
            occurredAt,
            new TurnPassedPayload(
                game.id(),
                game.version(),
                requesterUserId,
                state.publicState().tilePoolCount(),
                game.currentTurnUser().id(),
                game.currentTurnSeatOrder(),
                game.turnNumber(),
                game.currentTurnId(),
                OffsetDateTime.of(game.currentTurnStartedAt(), ZoneOffset.UTC),
                OffsetDateTime.of(game.currentTurnDeadlineAt(), ZoneOffset.UTC),
                game.consecutivePassCount()
            ),
            state.privateStates()
        ));
        return new GameTurnCommandResult(game.id(), PASS, game.version());
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }

    private Game requireLockedGame(long gameId) {
        return gameRepository.findByIdForUpdate(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
    }

    private static void requireInProgress(Game game) {
        if (game.status() != GameStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GAME_NOT_IN_PROGRESS);
        }
    }

    private List<GamePlayer> orderedPlayers(long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId).stream()
            .sorted(Comparator.comparingInt(GamePlayer::seatOrder))
            .toList();
        if (players.size() < 2 || players.size() > 4) {
            throw new IllegalStateException("an in-progress game must have two to four players");
        }
        return players;
    }

    private static GamePlayer requireMembership(List<GamePlayer> players, long userId) {
        return players.stream()
            .filter(player -> player.user().id() == userId)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED));
    }

    private static void requireExpectedVersion(Game game, long expectedGameVersion) {
        if (expectedGameVersion != game.version()) {
            throw new BusinessException(ErrorCode.STALE_GAME_VERSION);
        }
    }

    private static void requireCurrentTurn(Game game, long requesterUserId) {
        if (game.currentTurnUser().id() != requesterUserId) {
            throw new BusinessException(ErrorCode.NOT_CURRENT_TURN);
        }
    }

    private static GamePlayer nextPlayer(List<GamePlayer> players, int currentSeatOrder) {
        return players.stream()
            .filter(player -> player.seatOrder() > currentSeatOrder)
            .findFirst()
            .orElse(players.get(0));
    }

    private StateBundle loadState(Game game, List<GamePlayer> players) {
        List<GamePlayer> refreshedPlayers = players.stream()
            .sorted(Comparator.comparingInt(GamePlayer::seatOrder))
            .toList();
        var tiles = gameTileRepository.findByGameId(game.id());
        var melds = gameMeldRepository.findByGameId(game.id());
        GamePublicState publicState = stateAssembler.publicState(game, refreshedPlayers, tiles, melds);
        Map<Long, GamePrivateState> privateStates = stateAssembler.privateStates(game, refreshedPlayers, tiles, melds);
        return new StateBundle(publicState, privateStates);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record StateBundle(GamePublicState publicState, Map<Long, GamePrivateState> privateStates) {
    }
}
