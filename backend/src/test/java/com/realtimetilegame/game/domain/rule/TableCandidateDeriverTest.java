package com.realtimetilegame.game.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetilegame.game.domain.rule.rearrangement.TableCandidateDeriver;
import com.realtimetilegame.game.domain.rule.rearrangement.TableCandidateDeriver.TilePlacement;

class TableCandidateDeriverTest {
    private final TableCandidateDeriver deriver = new TableCandidateDeriver();

    @Test
    void beP7Fix001PlacementToCandidateDerivationUsesRowAndColumnOrder() {
        var result = deriver.derive(List.of(
            new TilePlacement("BLACK-10-A", 2, 6),
            new TilePlacement("RED-10-A", 2, 4),
            new TilePlacement("BLUE-10-A", 2, 5)
        ));

        assertThat(result).hasSize(1);

        /*
         * Do not use AssertJ satisfies(candidate -> ...).
         *
         * The lambda creates a synthetic test-class method whose parameter type is
         * TableCandidateDeriver.DerivedCandidate. JUnit discovery reflects over every
         * declared method before tests run, so stale or partially rebuilt nested class
         * output can abort the entire Jupiter engine before a single test executes.
         *
         * Reading the indexed result inside the test body keeps discovery independent
         * from that synthetic lambda signature while still testing the public API.
         */
        var candidate = result.get(0);
        assertThat(candidate.gridRow()).isEqualTo(2);
        assertThat(candidate.gridColumn()).isEqualTo(4);
        assertThat(candidate.tileIds())
            .containsExactly("RED-10-A", "BLUE-10-A", "BLACK-10-A");
    }

    @Test
    void beP7Fix002AdjacentPlacementAutomaticallyMergesCandidates() {
        var result = deriver.derive(List.of(
            new TilePlacement("RED-10-A", 0, 0),
            new TilePlacement("BLUE-10-A", 0, 1),
            new TilePlacement("BLACK-10-A", 0, 2)
        ));

        assertThat(result).hasSize(1);
    }

    @Test
    void beP7Fix003GapAutomaticallySplitsCandidates() {
        var result = deriver.derive(List.of(
            new TilePlacement("RED-10-A", 0, 0),
            new TilePlacement("BLACK-10-A", 0, 2)
        ));

        assertThat(result).hasSize(2);
    }
}
