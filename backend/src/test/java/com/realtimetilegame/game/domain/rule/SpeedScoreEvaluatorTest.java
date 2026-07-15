package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.P2;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimetilegame.game.domain.rule.end.OutcomeType;
import com.realtimetilegame.game.domain.rule.end.SpeedScoreContext;
import com.realtimetilegame.game.domain.rule.end.SpeedScoreEvaluator;
import com.realtimetilegame.game.domain.rule.end.TileContribution;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.tile.TileColor;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SpeedScoreEvaluatorTest {
    private final SpeedScoreEvaluator evaluator = new SpeedScoreEvaluator();

    @Test
    void joker010UsesAssignedNumberForContributedJoker() {
        var result = evaluator.evaluate(new SpeedScoreContext(
            Map.of(
                P1, List.of(new TileContribution(JOKER_A, P1, 11, 3)),
                P2, List.of(new TileContribution(id(TileColor.BLUE, 6), P2, 6, 3))
            ),
            Map.of(P1, RackState.empty(), P2, RackState.empty()),
            CATALOG
        ));

        assertThat(result.finalScores().get(P1)).isEqualTo(11);
        assertThat(result.outcome().winnerOptional()).contains(P1);
    }

    @Test
    void joker011PenalizesRemainingJokerByThirtyPoints() {
        var result = evaluator.evaluate(new SpeedScoreContext(
            Map.of(P1, List.of(), P2, List.of()),
            Map.of(P1, new RackState(List.of(JOKER_A)), P2, new RackState(List.of(id(TileColor.RED, 10)))),
            CATALOG
        ));

        assertThat(result.finalScores()).containsEntry(P1, -30).containsEntry(P2, -10);
        assertThat(result.outcome().winnerOptional()).contains(P2);
    }

    @Test
    void returnsSingleHighestFinalScoreAsWinner() {
        var result = evaluator.evaluate(new SpeedScoreContext(
            Map.of(
                P1, List.of(new TileContribution(id(TileColor.RED, 10), P1, 10, 1)),
                P2, List.of(new TileContribution(id(TileColor.BLUE, 5), P2, 5, 1))
            ),
            Map.of(P1, RackState.empty(), P2, RackState.empty()),
            CATALOG
        ));
        assertThat(result.outcome().type()).isEqualTo(OutcomeType.WIN);
        assertThat(result.outcome().winnerOptional()).contains(P1);
    }

    @Test
    void returnsDrawWhenHighestFinalScoreIsTied() {
        var result = evaluator.evaluate(new SpeedScoreContext(
            Map.of(P1, List.of(), P2, List.of()),
            Map.of(P1, RackState.empty(), P2, RackState.empty()),
            CATALOG
        ));
        assertThat(result.outcome().type()).isEqualTo(OutcomeType.DRAW);
    }
    @Test
    void rejectsContributedTileThatStillExistsInARack() {
        var red10 = id(TileColor.RED, 10);
        assertThatThrownBy(() -> new SpeedScoreContext(
            Map.of(
                P1, List.of(new TileContribution(red10, P1, 10, 1)),
                P2, List.of()
            ),
            Map.of(P1, new RackState(List.of(red10)), P2, RackState.empty()),
            CATALOG
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot remain");
    }

    @Test
    void rejectsSameRemainingTileAcrossParticipantRacks() {
        var red10 = id(TileColor.RED, 10);
        assertThatThrownBy(() -> new SpeedScoreContext(
            Map.of(P1, List.of(), P2, List.of()),
            Map.of(P1, new RackState(List.of(red10)), P2, new RackState(List.of(red10))),
            CATALOG
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than one rack");
    }

    @Test
    void preservesDeterministicParticipantOrderInScoreResult() {
        var contributions = new java.util.LinkedHashMap<com.realtimetilegame.game.domain.state.ParticipantId, List<TileContribution>>();
        contributions.put(P2, List.of());
        contributions.put(P1, List.of());
        var racks = new java.util.LinkedHashMap<com.realtimetilegame.game.domain.state.ParticipantId, RackState>();
        racks.put(P2, RackState.empty());
        racks.put(P1, RackState.empty());

        var result = evaluator.evaluate(new SpeedScoreContext(contributions, racks, CATALOG));

        assertThat(result.finalScores().keySet()).containsExactly(P1, P2);
    }

}
