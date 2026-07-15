package com.realtimetilegame.room.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.room.application.dto.ActiveRoomView;
import com.realtimetilegame.room.application.dto.RoomDetail;
import com.realtimetilegame.room.application.dto.RoomPage;
import com.realtimetilegame.room.application.dto.RoomParticipantView;
import com.realtimetilegame.room.application.dto.RoomStartEligibility;
import com.realtimetilegame.room.application.dto.RoomSummary;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomGameMode;
import com.realtimetilegame.room.domain.RoomPlayer;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;

@Service
public class RoomQueryService {
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository playerRepository;

    public RoomQueryService(RoomRepository roomRepository, RoomPlayerRepository playerRepository) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public RoomPage list(long userId, String status, String gameMode, int page, int size) {
        if (!RoomStatus.WAITING.name().equalsIgnoreCase(status == null ? "WAITING" : status)
            || !RoomGameMode.CLASSIC.name().equalsIgnoreCase(gameMode == null ? "CLASSIC" : gameMode)) {
            throw new BusinessException(ErrorCode.INVALID_GAME_MODE);
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        boolean hasActiveRoom = playerRepository.findActiveByUserId(userId).isPresent();
        List<RoomSummary> content = roomRepository.findWaitingClassicRooms(userId, safePage * safeSize, safeSize)
            .stream().map(view -> RoomSummary.from(view, hasActiveRoom)).toList();
        return new RoomPage(content, safePage, safeSize, roomRepository.countWaitingClassicRooms());
    }

    @Transactional(readOnly = true)
    public RoomSummary quickMatch(long userId, String gameMode) {
        if (!RoomGameMode.CLASSIC.name().equalsIgnoreCase(gameMode == null ? "CLASSIC" : gameMode)) {
            throw new BusinessException(ErrorCode.INVALID_GAME_MODE);
        }
        if (playerRepository.findActiveByUserId(userId).isPresent()) {
            return null;
        }
        return roomRepository.findQuickMatch(userId).map(view -> RoomSummary.from(view, false)).orElse(null);
    }

    @Transactional(readOnly = true)
    public RoomDetail detail(long roomId, long userId) {
        Room room = requireOpenRoom(roomId);
        if (!playerRepository.existsActiveByRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED);
        }
        List<RoomPlayer> players = playerRepository.findActiveByRoomId(roomId);
        return detailOf(room, players);
    }

    @Transactional(readOnly = true)
    public ActiveRoomView activeRoom(long userId) {
        return playerRepository.findActiveByUserId(userId)
            .filter(player -> player.room().status() != RoomStatus.CLOSED)
            .map(player -> ActiveRoomView.active(player.room().id(), player.room().status().name()))
            .orElseGet(ActiveRoomView::none);
    }

    public static RoomStartEligibility eligibility(Room room, List<RoomPlayer> players) {
        if (room.status() != RoomStatus.WAITING) {
            return new RoomStartEligibility(false, "ROOM_NOT_WAITING", players.size());
        }
        if (players.size() < 2) {
            return new RoomStartEligibility(false, "ROOM_MIN_PLAYERS_NOT_MET", players.size());
        }
        if (players.size() > room.maxPlayers()) {
            return new RoomStartEligibility(false, "ROOM_FULL", players.size());
        }
        boolean allReady = players.stream().allMatch(player -> player.readyStatus().name().equals("READY"));
        if (!allReady) {
            return new RoomStartEligibility(false, "ROOM_PLAYERS_NOT_READY", players.size());
        }
        return new RoomStartEligibility(true, null, players.size());
    }

    public static RoomDetail detailOf(Room room, List<RoomPlayer> players) {
        RoomStartEligibility eligibility = eligibility(room, players);
        return new RoomDetail(
            room.id(), room.roomName(), room.owner().id(), room.owner().nickname(), players.size(),
            room.maxPlayers(), room.gameMode().name(), room.turnTimeLimitSeconds(), room.status().name(),
            eligibility.startable(), eligibility.blockReason(),
            players.stream().map(RoomParticipantView::from).toList()
        );
    }

    private Room requireOpenRoom(long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.status() == RoomStatus.CLOSED) throw new BusinessException(ErrorCode.ROOM_CLOSED);
        return room;
    }
}
