package com.realtimetilegame.game.application.dto;

public record GamePlayerPublicView(
    long userId,
    String nickname,
    String avatarType,
    int seatOrder,
    int rackTileCount,
    boolean initialMeldCompleted,
    boolean currentTurn
) {
}
