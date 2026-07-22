package com.realtimetilegame.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PersistedTableGridLayoutResolverTest {
    private final PersistedTableGridLayoutResolver resolver = new PersistedTableGridLayoutResolver();

    @Test
    void keepsAlreadyBoundedCoordinatesUnchanged() {
        var resolved = resolver.resolve(List.of(
            placement("a", 3, 2, 8),
            placement("b", 4, 4, 10)
        ));

        assertThat(resolved.get("a")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(2, 8));
        assertThat(resolved.get("b")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(4, 10));
    }

    @Test
    void deterministicallyProjectsLegacyColumnsWithoutMutatingCandidateOrder() {
        var resolved = resolver.resolve(List.of(
            placement("a", 3, 2, 8),
            placement("b", 3, 4, 18),
            placement("c", 5, 7, 20),
            placement("d", 4, 8, 0)
        ));

        assertThat(resolved.get("a")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(0, 0));
        assertThat(resolved.get("b")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(0, 4));
        assertThat(resolved.get("c")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(0, 8));
        assertThat(resolved.get("d")).isEqualTo(new PersistedTableGridLayoutResolver.Coordinate(0, 14));
    }

    @Test
    void rejectsLegacyStateThatCannotFitEighteenRows() {
        List<PersistedTableGridLayoutResolver.StoredPlacement> placements = new ArrayList<>();
        for (int index = 0; index < 19; index++) {
            placements.add(placement("meld-" + index, 18, 0, 20));
        }

        assertThatThrownBy(() -> resolver.resolve(placements))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("persisted table layout cannot fit bounded grid");
    }

    private static PersistedTableGridLayoutResolver.StoredPlacement placement(
        String meldId,
        int tileCount,
        int gridRow,
        int gridColumn
    ) {
        return new PersistedTableGridLayoutResolver.StoredPlacement(meldId, tileCount, gridRow, gridColumn);
    }
}
