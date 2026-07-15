package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;

import java.util.LinkedHashMap;
import java.util.Map;

public record SpeedScoreResult(
    Map<ParticipantId, Integer> finalScores,
    GameOutcomeCandidate outcome
) {
    public SpeedScoreResult {
        finalScores = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(finalScores));
        outcome = java.util.Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
