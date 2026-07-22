package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CANONICAL_IDS;
import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_B;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.state;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.model.TurnValidationContext;
import com.realtimetilegame.game.domain.rule.model.ValidatedTurn;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.game.domain.rule.policy.SpeedRulePolicy;
import com.realtimetilegame.game.domain.rule.turn.TurnCommitValidator;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileColor;

import org.junit.jupiter.api.Test;

class InitialTurnValidationTest {
    private final TurnCommitValidator validator = new TurnCommitValidator();

    @Test
    void init009TurnValidationMarksClassicInitialMeldCompleted() {
        var r10 = id(TileColor.RED, 10);
        var b10 = id(TileColor.BLUE, 10);
        var y10 = id(TileColor.YELLOW, 10);
        var start = state(TableState.empty(), r10, b10, y10);
        var candidate = state(table(meld("G10", r10, b10, y10)));

        var result = validator.validate(new TurnValidationContext(
            GameMode.CLASSIC,
            P1,
            CATALOG,
            CANONICAL_IDS,
            start,
            candidate,
            false,
            new ClassicRulePolicy()
        ));

        ValidatedTurn validated = ((ValidationSuccess<ValidatedTurn>) result).value();
        assertThat(validated.initialMeldCompletedAfterValidation()).isTrue();
        assertThat(validated.initialMeldScore()).isEqualTo(30);
    }

    @Test
    void init011SpeedTurnSkipsThirtyPointThreshold() {
        var r1 = id(TileColor.RED, 1);
        var b1 = id(TileColor.BLUE, 1);
        var y1 = id(TileColor.YELLOW, 1);
        var start = state(TableState.empty(), r1, b1, y1);
        var candidate = state(table(meld("G1", r1, b1, y1)));

        var result = validator.validate(new TurnValidationContext(
            GameMode.SPEED,
            P1,
            CATALOG,
            CANONICAL_IDS,
            start,
            candidate,
            false,
            new SpeedRulePolicy()
        ));

        ValidatedTurn validated = ((ValidationSuccess<ValidatedTurn>) result).value();
        assertThat(validated.initialMeldCompletedAfterValidation()).isTrue();
        assertThat(validated.initialMeldScore()).isZero();
    }

    @Test
    void initialMeldRackContributionOrderFollowsTurnStartRackOrder() {
        var yellow10 = id(TileColor.YELLOW, 10);
        var red10 = id(TileColor.RED, 10);
        var blue10 = id(TileColor.BLUE, 10);
        var start = state(TableState.empty(), yellow10, red10, blue10);
        var candidate = state(table(meld("G10", red10, blue10, yellow10)));

        ValidatedTurn validated = validateClassicInitial(start, candidate);

        assertThat(validated.rackToTableTiles()).containsExactly(yellow10, red10, blue10);
    }

    @Test
    void repeatedValidationReturnsSameOrderedCollections() {
        var red10 = id(TileColor.RED, 10);
        var start = state(TableState.empty(), JOKER_B, red10, JOKER_A);
        var candidate = state(table(meld("G10", red10, JOKER_B, JOKER_A)));

        for (int attempt = 0; attempt < 20; attempt++) {
            ValidatedTurn validated = validateClassicInitial(start, candidate);
            assertThat(validated.rackToTableTiles()).containsExactly(JOKER_B, red10, JOKER_A);
            assertThat(validated.validatedMelds().get(0).jokerAssignments().keySet())
                .containsExactly(JOKER_B, JOKER_A);
        }
    }

    private ValidatedTurn validateClassicInitial(
        com.realtimetilegame.game.domain.state.TileLocationState start,
        com.realtimetilegame.game.domain.state.TileLocationState candidate
    ) {
        var result = validator.validate(new TurnValidationContext(
            GameMode.CLASSIC,
            P1,
            CATALOG,
            CANONICAL_IDS,
            start,
            candidate,
            false,
            new ClassicRulePolicy()
        ));
        return ((ValidationSuccess<ValidatedTurn>) result).value();
    }
}
