package com.realtimetilegame.game.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator;
import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator.MeldPlacement;

class TableGridLayoutValidatorTest {
    private final TableGridLayoutValidator validator = new TableGridLayoutValidator();

    @Test
    void beP7B002RejectsOverlappingCells() {
        assertThat(validator.isValid(List.of(
            new MeldPlacement("first", 3, 2, 4),
            new MeldPlacement("second", 4, 2, 6)
        ))).isFalse();
    }

    @Test
    void beP7B003RejectsRowsAndColumnsOutsideTheGrid() {
        assertThat(validator.isValid(List.of(
            new MeldPlacement("row", 3, TableGridLayoutValidator.ROWS, 0)
        ))).isFalse();
        assertThat(validator.isValid(List.of(
            new MeldPlacement("column", 3, 0, TableGridLayoutValidator.COLUMNS - 2)
        ))).isFalse();
    }

    @Test
    void acceptsOnlyTheInclusiveZeroToSeventeenEdges() {
        assertThat(validator.isValid(List.of(
            new MeldPlacement("top-left", 1, 0, 0),
            new MeldPlacement("bottom-right", 1, 17, 17)
        ))).isTrue();
        assertThat(validator.isValid(List.of(new MeldPlacement("negative-row", 1, -1, 0)))).isFalse();
        assertThat(validator.isValid(List.of(new MeldPlacement("negative-column", 1, 0, -1)))).isFalse();
        assertThat(validator.isValid(List.of(new MeldPlacement("row-eighteen", 1, 18, 0)))).isFalse();
        assertThat(validator.isValid(List.of(new MeldPlacement("column-eighteen", 1, 0, 18)))).isFalse();
    }

    @Test
    void acceptsSeparatedHorizontalMeldsAtLogicalCoordinates() {
        assertThat(validator.isValid(List.of(
            new MeldPlacement("first", 7, 0, 0),
            new MeldPlacement("second", 7, 0, 8),
            new MeldPlacement("third", 3, 1, 7)
        ))).isTrue();
    }
}
