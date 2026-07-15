package com.realtimetilegame.game.application.dto;

public record GameStartedPayload(
    long gameId,
    long roomId,
    String route,
    GamePublicState publicState
) {
}
