package com.realtimetilegame.game.application.dto;

public record GameStartResult(
    long gameId,
    long roomId,
    String status,
    long currentTurnUserId,
    int currentTurnSeatOrder,
    int turnNumber,
    int playerCount
) {
}
