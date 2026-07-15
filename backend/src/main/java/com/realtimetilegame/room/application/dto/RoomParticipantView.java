package com.realtimetilegame.room.application.dto;

import com.realtimetilegame.room.domain.RoomPlayer;

public record RoomParticipantView(
    long userId,
    String nickname,
    String avatarType,
    int seatOrder,
    String readyStatus,
    boolean owner
) {
    public static RoomParticipantView from(RoomPlayer player) {
        return new RoomParticipantView(
            player.user().id(), player.user().nickname(), player.user().avatarType().name(),
            player.seatOrder(), player.readyStatus().name(), player.owner()
        );
    }
}
