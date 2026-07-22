package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MeldsCommittedPayload(
    long gameId,
    long gameVersion,
    long committedByUserId,
    int committedByRackCount,
    boolean initialMeldCompleted,
    int initialMeldScore,
    List<String> changedMeldIds,
    int rackContributionCount,
    boolean tableRecomposed,
    long nextTurnUserId,
    int nextTurnSeatOrder,
    int turnNumber,
    String currentTurnId,
    OffsetDateTime currentTurnStartedAt,
    OffsetDateTime turnDeadlineAt,
    int consecutivePassCount
) {
    public MeldsCommittedPayload {
        changedMeldIds = List.copyOf(changedMeldIds);
    }
}
