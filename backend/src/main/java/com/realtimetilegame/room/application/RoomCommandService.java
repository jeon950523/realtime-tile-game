package com.realtimetilegame.room.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.room.application.dto.RoomDetail;
import com.realtimetilegame.room.application.dto.RoomParticipantView;
import com.realtimetilegame.room.application.dto.RoomStartEligibility;
import com.realtimetilegame.room.application.dto.RoomSummary;
import com.realtimetilegame.room.domain.ReadyStatus;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomGameMode;
import com.realtimetilegame.room.domain.RoomPlayer;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.room.event.RoomEventEnvelope;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class RoomCommandService {
    private static final String LOBBY_DESTINATION = "/topic/lobby/rooms";

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository playerRepository;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    public RoomCommandService(UserRepository userRepository, RoomRepository roomRepository,
                              RoomPlayerRepository playerRepository, RoomEventPublisher eventPublisher,
                              Clock clock) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public RoomDetail create(long userId, String roomName, int maxPlayers, String gameMode,
                             int turnTimeLimitSeconds, boolean publicRoom) {
        User user = requireLockedActiveUser(userId);
        requireNoActiveRoom(userId);
        validateCreation(maxPlayers, gameMode, turnTimeLimitSeconds, publicRoom);
        LocalDateTime now = now();
        Room room;
        try {
            room = roomRepository.saveAndFlush(Room.createClassic(roomName, user, maxPlayers, turnTimeLimitSeconds, now));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        RoomPlayer owner = playerRepository.saveAndFlush(RoomPlayer.join(room, user, 1, true, now));
        List<RoomPlayer> players = List.of(owner);
        publishLobby("ROOM_CREATED", summary(room, 1));
        return roomState(room, players);
    }

    @Transactional
    public RoomDetail join(long roomId, long userId) {
        User user = requireLockedActiveUser(userId);
        requireNoActiveRoom(userId);
        Room room = requireLockedWaitingRoom(roomId);
        if (!room.publicRoom()) throw new BusinessException(ErrorCode.PRIVATE_ROOM_NOT_SUPPORTED);
        List<RoomPlayer> players = playerRepository.findActiveByRoomIdForUpdate(roomId);
        if (players.size() >= room.maxPlayers()) throw new BusinessException(ErrorCode.ROOM_FULL);
        int seat = smallestAvailableSeat(players, room.maxPlayers());
        RoomPlayer joined = playerRepository.saveAndFlush(RoomPlayer.join(room, user, seat, false, now()));
        List<RoomPlayer> updated = append(players, joined);
        RoomDetail snapshot = roomState(room, updated);
        publishRoom(roomId, "ROOM_PLAYER_JOINED", RoomParticipantView.from(joined));
        publishRoomState(roomId, snapshot);
        publishLobby("ROOM_UPDATED", summary(room, updated.size()));
        return snapshot;
    }

    @Transactional
    public void leave(long roomId, long userId) {
        requireLockedActiveUser(userId);
        Room room = requireLockedWaitingRoom(roomId);
        List<RoomPlayer> players = playerRepository.findActiveByRoomIdForUpdate(roomId);
        RoomPlayer leaving = players.stream().filter(player -> player.user().id() == userId).findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED));
        LocalDateTime now = now();
        boolean wasOwner = leaving.owner();
        leaving.leave(now);
        playerRepository.saveAndFlush(leaving);
        List<RoomPlayer> remaining = players.stream().filter(player -> player != leaving).toList();
        publishRoom(roomId, "ROOM_PLAYER_LEFT", java.util.Map.of("roomId", roomId, "userId", userId));
        if (remaining.isEmpty()) {
            room.close(now);
            roomRepository.saveAndFlush(room);
            publishRoom(roomId, "ROOM_CLOSED", java.util.Map.of("roomId", roomId));
            publishLobby("ROOM_REMOVED", java.util.Map.of("roomId", roomId));
            return;
        }
        if (wasOwner) {
            RoomPlayer nextOwner = remaining.stream()
                .min(java.util.Comparator.comparing(RoomPlayer::joinedAt).thenComparing(RoomPlayer::id))
                .orElseThrow();
            nextOwner.promoteOwner();
            playerRepository.saveAndFlush(nextOwner);
            room.transferOwnership(nextOwner.user(), now);
            roomRepository.saveAndFlush(room);
            publishRoom(roomId, "ROOM_OWNER_CHANGED", RoomParticipantView.from(nextOwner));
        }
        publishRoomState(roomId, roomState(room, remaining));
        publishLobby("ROOM_UPDATED", summary(room, remaining.size()));
    }

    @Transactional
    public ReadyChangeResult changeReady(long roomId, long userId, boolean ready) {
        requireLockedActiveUser(userId);
        Room room = requireLockedWaitingRoom(roomId);
        List<RoomPlayer> players = playerRepository.findActiveByRoomIdForUpdate(roomId);
        RoomPlayer current = players.stream().filter(player -> player.user().id() == userId).findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED));
        boolean changed = current.changeReady(ready);
        if (changed) playerRepository.saveAndFlush(current);
        RoomStartEligibility eligibility = RoomQueryService.eligibility(room, players);
        if (changed) {
            publishRoom(roomId, "ROOM_READY_CHANGED", java.util.Map.of(
                "roomId", roomId,
                "userId", userId,
                "readyStatus", current.readyStatus().name(),
                "startable", eligibility.startable(),
                "startBlockReason", eligibility.blockReason() == null ? "" : eligibility.blockReason()
            ));
            publishRoomState(roomId, roomState(room, players));
        }
        return new ReadyChangeResult(changed, current.readyStatus(), eligibility);
    }

    private User requireLockedActiveUser(long userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }

    private void requireNoActiveRoom(long userId) {
        if (playerRepository.findActiveByUserId(userId).isPresent()) throw new BusinessException(ErrorCode.USER_ALREADY_IN_ROOM);
    }

    private Room requireLockedWaitingRoom(long roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.status() == RoomStatus.CLOSED) throw new BusinessException(ErrorCode.ROOM_CLOSED);
        if (room.status() != RoomStatus.WAITING) throw new BusinessException(ErrorCode.ROOM_ALREADY_PLAYING);
        return room;
    }

    private static void validateCreation(int maxPlayers, String gameMode, int turnLimit, boolean publicRoom) {
        if (maxPlayers < 2 || maxPlayers > 4) throw new BusinessException(ErrorCode.INVALID_MAX_PLAYERS);
        RoomGameMode parsed;
        try { parsed = RoomGameMode.parse(gameMode); } catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_GAME_MODE); }
        if (parsed != RoomGameMode.CLASSIC) throw new BusinessException(ErrorCode.INVALID_GAME_MODE);
        if (turnLimit < 30 || turnLimit > 300) throw new BusinessException(ErrorCode.INVALID_TIME_LIMIT);
        if (!publicRoom) throw new BusinessException(ErrorCode.PRIVATE_ROOM_NOT_SUPPORTED);
    }

    private static int smallestAvailableSeat(List<RoomPlayer> players, int maxPlayers) {
        Set<Integer> used = new HashSet<>();
        players.forEach(player -> used.add(player.seatOrder()));
        for (int seat = 1; seat <= maxPlayers; seat++) if (!used.contains(seat)) return seat;
        throw new BusinessException(ErrorCode.ROOM_FULL);
    }

    private RoomSummary summary(Room room, int playerCount) {
        return new RoomSummary(room.id(), room.roomName(), room.owner().nickname(), playerCount,
            room.maxPlayers(), room.gameMode().name(), room.turnTimeLimitSeconds(), room.status().name(),
            room.status() == RoomStatus.WAITING && playerCount < room.maxPlayers());
    }

    private RoomDetail roomState(Room room, List<RoomPlayer> players) {
        return RoomQueryService.detailOf(room, players.stream()
            .sorted(java.util.Comparator.comparingInt(RoomPlayer::seatOrder))
            .toList());
    }

    private void publishLobby(String type, Object payload) { publish(LOBBY_DESTINATION, type, payload); }
    private void publishRoom(long roomId, String type, Object payload) { publish("/topic/rooms/" + roomId, type, payload); }
    private void publishRoomState(long roomId, RoomDetail snapshot) { publishRoom(roomId, "ROOM_STATE_UPDATED", snapshot); }
    private void publish(String destination, String type, Object payload) {
        eventPublisher.publish(new RoomEventEnvelope(destination,
            new RealtimeEvent(type, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), payload)));
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static List<RoomPlayer> append(List<RoomPlayer> players, RoomPlayer joined) {
        java.util.ArrayList<RoomPlayer> result = new java.util.ArrayList<>(players);
        result.add(joined);
        return result;
    }

    public record ReadyChangeResult(boolean changed, ReadyStatus readyStatus, RoomStartEligibility eligibility) {}
}
