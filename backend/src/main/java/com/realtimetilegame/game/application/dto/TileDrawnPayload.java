package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;

public record TileDrawnPayload(
    long gameId,
    long gameVersion,
    long drawnByUserId,
    int drawnByRackCount,
    int tilePoolCount,
    long nextTurnUserId,
    int nextTurnSeatOrder,
    int turnNumber,
    String currentTurnId,
    OffsetDateTime currentTurnStartedAt,
    OffsetDateTime turnDeadlineAt,
    int consecutivePassCount
) {
}
