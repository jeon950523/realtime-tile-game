package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CANONICAL_IDS;
import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.state;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimetilegame.game.domain.rule.model.TurnValidationContext;
import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.game.domain.rule.policy.SpeedRulePolicy;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

class TurnValidationContextTest {
    @Test
    void rejectsModeAndPolicyMismatch() {
        var state = state(TableState.empty());

        assertThatThrownBy(() -> new TurnValidationContext(
            GameMode.CLASSIC,
            P1,
            CATALOG,
            CANONICAL_IDS,
            state,
            state,
            true,
            new SpeedRulePolicy()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same mode");
    }

    @Test
    void rejectsCanonicalSetThatDoesNotMatchCatalog() {
        var state = state(TableState.empty());
        var incompleteCanonical = new LinkedHashSet<>(CANONICAL_IDS);
        incompleteCanonical.remove(new TileId("JOKER-B"));

        assertThatThrownBy(() -> new TurnValidationContext(
            GameMode.CLASSIC,
            P1,
            CATALOG,
            incompleteCanonical,
            state,
            state,
            true,
            new ClassicRulePolicy()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("catalog exactly");
    }
}
