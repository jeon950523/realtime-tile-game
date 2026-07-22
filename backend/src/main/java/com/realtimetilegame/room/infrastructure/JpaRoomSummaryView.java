package com.realtimetilegame.room.infrastructure;

import java.time.LocalDateTime;

import com.realtimetilegame.room.domain.RoomGameMode;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.room.domain.RoomSummaryView;

public record JpaRoomSummaryView(
    long roomId,
    String roomName,
    String ownerNickname,
    int currentPlayers,
    int maxPlayers,
    RoomGameMode gameMode,
    int turnTimeLimitSeconds,
    RoomStatus status,
    LocalDateTime createdAt
) implements RoomSummaryView {
    public JpaRoomSummaryView(Long roomId, String roomName, String ownerNickname, Long currentPlayers,
                              int maxPlayers, RoomGameMode gameMode, int turnTimeLimitSeconds,
                              RoomStatus status, LocalDateTime createdAt) {
        this(roomId.longValue(), roomName, ownerNickname, currentPlayers.intValue(), maxPlayers,
            gameMode, turnTimeLimitSeconds, status, createdAt);
    }
}
