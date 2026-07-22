package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.P2;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.end.DeadlockContext;
import com.realtimetilegame.game.domain.rule.end.DeadlockEvaluator;
import com.realtimetilegame.game.domain.rule.end.OutcomeType;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DeadlockEvaluatorTest {
    private final DeadlockEvaluator evaluator = new DeadlockEvaluator();

    @Test
    void doesNotEndWhenPoolIsNotEmpty() {
        assertThat(evaluator.evaluate(new DeadlockContext(false, 2, Map.of(P1, 10, P2, 20)))).isEmpty();
    }

    @Test
    void doesNotEndBeforeEveryActiveParticipantPasses() {
        assertThat(evaluator.evaluate(new DeadlockContext(true, 1, Map.of(P1, 10, P2, 20)))).isEmpty();
    }

    @Test
    void returnsSingleLowestRackScoreAsWinCandidate() {
        var outcome = evaluator.evaluate(new DeadlockContext(true, 2, Map.of(P1, 10, P2, 20))).orElseThrow();
        assertThat(outcome.type()).isEqualTo(OutcomeType.WIN);
        assertThat(outcome.winnerOptional()).contains(P1);
    }

    @Test
    void returnsDrawWhenLowestRackScoreIsTied() {
        var outcome = evaluator.evaluate(new DeadlockContext(true, 2, Map.of(P1, 10, P2, 10))).orElseThrow();
        assertThat(outcome.type()).isEqualTo(OutcomeType.DRAW);
        assertThat(outcome.winnerOptional()).isEmpty();
    }
}
