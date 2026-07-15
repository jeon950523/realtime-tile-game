package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DeadlockContext(
    boolean tilePoolEmpty,
    int consecutivePassCount,
    Map<ParticipantId, Integer> remainingRackScores
) {
    public DeadlockContext {
        if (consecutivePassCount < 0) {
            throw new IllegalArgumentException("consecutivePassCount must not be negative");
        }
        Objects.requireNonNull(remainingRackScores, "remainingRackScores must not be null");
        remainingRackScores = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(remainingRackScores));
        if (remainingRackScores.size() < 2 || remainingRackScores.size() > 4) {
            throw new IllegalArgumentException("active participant count must be between 2 and 4");
        }
        if (remainingRackScores.values().stream().anyMatch(score -> score == null || score < 0)) {
            throw new IllegalArgumentException("remaining rack scores must be non-negative");
        }
    }
}
