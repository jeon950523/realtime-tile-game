package com.realtimetilegame.game.application.dto;

public record GameTurnCommandResult(
    long gameId,
    String actionType,
    long gameVersion
) {
}
