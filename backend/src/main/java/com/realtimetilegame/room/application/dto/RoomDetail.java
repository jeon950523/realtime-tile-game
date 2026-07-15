package com.realtimetilegame.room.application.dto;

import java.util.List;

public record RoomDetail(
    long roomId,
    String roomName,
    long ownerUserId,
    String ownerNickname,
    int currentPlayers,
    int maxPlayers,
    String gameMode,
    int turnTimeLimitSeconds,
    String status,
    boolean startable,
    String startBlockReason,
    List<RoomParticipantView> participants
) {
    public RoomDetail {
        participants = List.copyOf(participants);
    }
}
