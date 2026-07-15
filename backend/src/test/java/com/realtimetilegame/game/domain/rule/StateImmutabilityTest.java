package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.state;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimetilegame.game.domain.tile.TileColor;

import org.junit.jupiter.api.Test;

class StateImmutabilityTest {
    @Test
    void stateCollectionsCannotBeModifiedExternally() {
        var state = state(
            table(meld("RUN", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5))),
            id(TileColor.BLACK, 13)
        );

        assertThatThrownBy(() -> state.racks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.rackOf(P1).tileIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.table().melds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.table().melds().get(0).tileIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.tilePool().remainingTileIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
