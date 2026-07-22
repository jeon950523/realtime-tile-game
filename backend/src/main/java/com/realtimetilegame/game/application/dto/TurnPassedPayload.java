package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;

public record TurnPassedPayload(
    long gameId,
    long gameVersion,
    long passedByUserId,
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
