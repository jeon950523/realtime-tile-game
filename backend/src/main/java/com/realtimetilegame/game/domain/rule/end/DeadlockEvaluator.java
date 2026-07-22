package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeadlockEvaluator {
    public Optional<GameOutcomeCandidate> evaluate(DeadlockContext context) {
        if (!context.tilePoolEmpty()
            || context.consecutivePassCount() < context.remainingRackScores().size()) {
            return Optional.empty();
        }

        int minimumScore = context.remainingRackScores().values().stream().min(Integer::compareTo).orElseThrow();
        List<ParticipantId> lowest = context.remainingRackScores().entrySet().stream()
            .filter(entry -> entry.getValue() == minimumScore)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing(ParticipantId::value))
            .toList();
        return Optional.of(lowest.size() == 1
            ? GameOutcomeCandidate.win(lowest.get(0))
            : GameOutcomeCandidate.draw());
    }
}
