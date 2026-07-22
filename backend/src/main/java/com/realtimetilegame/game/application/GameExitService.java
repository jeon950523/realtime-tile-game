package com.realtimetilegame.game.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.GameExitResult;
import com.realtimetilegame.game.application.dto.GameTerminatedPayload;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomPlayer;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.room.application.RoomEventPublisher;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.room.event.RoomEventEnvelope;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class GameExitService {
    public static final String EXIT_ACTIVE_GAME = "EXIT_ACTIVE_GAME";

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameEventPublisher eventPublisher;
    private final RoomEventPublisher roomEventPublisher;
    private final Clock clock;

    public GameExitService(UserRepository userRepository,
                           GameRepository gameRepository,
                           GamePlayerRepository gamePlayerRepository,
                           RoomRepository roomRepository,
                           RoomPlayerRepository roomPlayerRepository,
                           GameEventPublisher eventPublisher,
                           RoomEventPublisher roomEventPublisher,
                           Clock clock) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.eventPublisher = eventPublisher;
        this.roomEventPublisher = roomEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GameExitResult exit(long roomId, long gameId, long requesterUserId, long expectedGameVersion) {
        requireActiveUser(requesterUserId);
        Game game = gameRepository.findByIdForUpdate(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        if (game.room().id() != roomId) throw new BusinessException(ErrorCode.ROOM_GAME_MISMATCH);
        if (game.status() != GameStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GAME_NOT_IN_PROGRESS);
        }

        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId).stream()
            .sorted(Comparator.comparingInt(GamePlayer::seatOrder))
            .toList();
        GamePlayer requester = players.stream()
            .filter(player -> player.user().id() == requesterUserId)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED));
        if (game.version() != expectedGameVersion) {
            throw new BusinessException(ErrorCode.STALE_GAME_VERSION);
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        List<RoomPlayer> activeRoomPlayers = roomPlayerRepository.findActiveByRoomIdForUpdate(roomId);
        if (activeRoomPlayers.stream().noneMatch(player -> player.user().id() == requesterUserId)) {
            throw new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Long winnerParticipantId = null;
        Long winnerUserId = null;
        requester.forfeit();
        if (players.size() == 2) {
            GamePlayer winner = players.stream().filter(player -> !player.id().equals(requester.id()))
                .findFirst().orElseThrow();
            winner.winByForfeit();
            game.finishByForfeit(winner.user(), now);
            winnerParticipantId = winner.id();
            winnerUserId = winner.user().id();
        } else {
            players.stream().filter(player -> !player.id().equals(requester.id())).forEach(GamePlayer::abort);
            game.abortByPlayerLeft(now);
        }

        activeRoomPlayers.forEach(player -> player.leave(now));
        room.close(now);
        gamePlayerRepository.saveAllAndFlush(players);
        roomPlayerRepository.saveAllAndFlush(activeRoomPlayers);
        roomRepository.saveAndFlush(room);
        gameRepository.saveAndFlush(game);

        OffsetDateTime serverTime = OffsetDateTime.of(now, ZoneOffset.UTC);
        GameTerminatedPayload terminatedPayload = new GameTerminatedPayload(
            room.id(), game.id(), game.version(), room.status().name(), game.status().name(),
            game.terminationReason().name(), requester.id(), requester.user().id(),
            winnerParticipantId, winnerUserId, serverTime
        );
        eventPublisher.publish(new GameTurnCommittedEvent(
            game.id(),
            "GAME_TERMINATED",
            serverTime,
            terminatedPayload,
            java.util.Map.of()
        ));
        roomEventPublisher.publish(new RoomEventEnvelope(
            "/topic/rooms/" + room.id(),
            new RealtimeEvent("ROOM_CLOSED", serverTime, java.util.Map.of(
                "roomId", room.id(),
                "gameId", game.id(),
                "terminationReason", game.terminationReason().name()
            ))
        ));
        roomEventPublisher.publish(new RoomEventEnvelope(
            "/topic/lobby/rooms",
            new RealtimeEvent("ROOM_REMOVED", serverTime, java.util.Map.of("roomId", room.id()))
        ));
        return new GameExitResult(game.id(), EXIT_ACTIVE_GAME, game.version());
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }
}
