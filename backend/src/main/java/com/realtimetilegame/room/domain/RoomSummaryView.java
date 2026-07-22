package com.realtimetilegame.room.domain;

import java.time.LocalDateTime;

public interface RoomSummaryView {
    long roomId();
    String roomName();
    String ownerNickname();
    int currentPlayers();
    int maxPlayers();
    RoomGameMode gameMode();
    int turnTimeLimitSeconds();
    RoomStatus status();
    LocalDateTime createdAt();
}
