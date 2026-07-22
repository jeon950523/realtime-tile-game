package com.realtimetilegame.room.application.dto;

import com.realtimetilegame.room.domain.RoomSummaryView;

public record RoomSummary(
    long roomId,
    String roomName,
    String ownerNickname,
    int currentPlayers,
    int maxPlayers,
    String gameMode,
    int turnTimeLimitSeconds,
    String status,
    boolean joinable
) {
    public static RoomSummary from(RoomSummaryView view, boolean userHasActiveRoom) {
        return new RoomSummary(
            view.roomId(), view.roomName(), view.ownerNickname(), view.currentPlayers(), view.maxPlayers(),
            view.gameMode().name(), view.turnTimeLimitSeconds(), view.status().name(),
            !userHasActiveRoom && view.currentPlayers() < view.maxPlayers()
        );
    }
}
