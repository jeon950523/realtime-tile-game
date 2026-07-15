package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CANONICAL_IDS;
import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.state;
import static com.realtimetilegame.game.support.RuleTestFixtures.stateWithPool;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.TurnValidationContext;
import com.realtimetilegame.game.domain.rule.model.ValidatedTurn;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.game.domain.rule.turn.TurnCommitValidator;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TurnCommitValidatorTest {
    private final TurnCommitValidator validator = new TurnCommitValidator();

    @Test
    void turn001AcceptsOneRackTileContribution() {
        TableState startTable = table(meld("G7", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7)));
        TileId contributed = id(TileColor.BLACK, 7);
        TileLocationState start = state(startTable, contributed);
        TileLocationState candidate = state(table(meld(
            "G7", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7), contributed
        )));

        ValidatedTurn turn = success(start, candidate);
        assertThat(turn.rackToTableTiles()).containsExactly(contributed);
    }

    @Test
    void turn002RejectsTableOnlyRearrangementWithoutRackContribution() {
        TableState table = table(meld("R", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5)));
        TileLocationState start = state(table, id(TileColor.BLACK, 9));
        TileLocationState candidate = state(table, id(TileColor.BLACK, 9));

        assertFailure(validate(start, candidate), RuleErrorCode.NO_RACK_TILE_USED);
    }

    @Test
    void turn003AcceptsSplittingLongRunWhenEveryFinalMeldIsValid() {
        TileId rackBlue8 = id(TileColor.BLUE, 8, "B");
        TableState startTable = table(meld(
            "LONG", id(TileColor.BLUE, 6), id(TileColor.BLUE, 7), id(TileColor.BLUE, 8),
            id(TileColor.BLUE, 9), id(TileColor.BLUE, 10)
        ));
        TableState candidateTable = table(
            meld("LEFT", id(TileColor.BLUE, 6), id(TileColor.BLUE, 7), id(TileColor.BLUE, 8)),
            meld("RIGHT", rackBlue8, id(TileColor.BLUE, 9), id(TileColor.BLUE, 10))
        );

        assertThat(success(state(startTable, rackBlue8), state(candidateTable)).validatedMelds()).hasSize(2);
    }

    @Test
    void turn004AcceptsAddingFourthColorToGroup() {
        TableState startTable = table(meld("G6", id(TileColor.RED, 6), id(TileColor.BLUE, 6), id(TileColor.YELLOW, 6)));
        TileId black6 = id(TileColor.BLACK, 6);
        TableState candidateTable = table(meld(
            "G6", id(TileColor.RED, 6), id(TileColor.BLUE, 6), id(TileColor.YELLOW, 6), black6
        ));

        assertThat(success(state(startTable, black6), state(candidateTable)).rackToTableTiles()).contains(black6);
    }

    @Test
    void turn005RejectsMovingExistingTableTileIntoRack() {
        TileId red3 = id(TileColor.RED, 3);
        TileId red6 = id(TileColor.RED, 6);
        TableState startTable = table(meld("R", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)));
        TableState candidateTable = table(meld("R", id(TileColor.RED, 4), id(TileColor.RED, 5), red6));
        TileLocationState start = state(startTable, red6);
        TileLocationState candidate = state(candidateTable, Map.of(P1, new RackState(List.of(red3))));

        assertFailure(validate(start, candidate), RuleErrorCode.TILE_NOT_OWNED);
    }

    @Test
    void turn006RejectsWholeTurnWhenOneMeldIsInvalid() {
        TileId[] rack = {
            id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5),
            id(TileColor.BLUE, 7), id(TileColor.BLUE, 8), id(TileColor.BLUE, 10)
        };
        TableState candidateTable = table(
            meld("VALID", rack[0], rack[1], rack[2]),
            meld("INVALID", rack[3], rack[4], rack[5])
        );

        assertFailure(validate(state(TableState.empty(), rack), state(candidateTable)), RuleErrorCode.INVALID_TABLE_LAYOUT);
    }

    @Test
    void turn007RejectsMissingExistingTile() {
        TileId red3 = id(TileColor.RED, 3);
        TileId red6 = id(TileColor.RED, 6);
        TableState startTable = table(meld("R", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)));
        TileLocationState start = state(startTable, red6);
        TableState candidateTable = table(meld("R", id(TileColor.RED, 4), id(TileColor.RED, 5), red6));
        TileLocationState candidate = stateWithPool(
            candidateTable,
            Map.of(P1, RackState.empty()),
            start.tilePool().remainingTileIds()
        );

        assertFailure(validate(start, candidate), RuleErrorCode.MISSING_TILE);
    }

    @Test
    void turn008RejectsPhysicalTileDuplicatedAcrossValidMelds() {
        TileId red3 = id(TileColor.RED, 3);
        TileId blue3 = id(TileColor.BLUE, 3);
        TileId yellow3 = id(TileColor.YELLOW, 3);
        TableState startTable = table(meld("RUN", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)));
        TableState candidateTable = table(
            meld("RUN", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)),
            meld("GROUP", red3, blue3, yellow3)
        );

        assertFailure(
            validate(state(startTable, blue3, yellow3), state(candidateTable)),
            RuleErrorCode.DUPLICATED_TILE
        );
    }

    @Test
    void turn009ValidationFailureLeavesBothInputSnapshotsUnchanged() {
        TileId red3 = id(TileColor.RED, 3);
        TileId blue3 = id(TileColor.BLUE, 3);
        TileId yellow3 = id(TileColor.YELLOW, 3);
        TableState startTable = table(meld("RUN", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)));
        TileLocationState start = state(startTable, blue3, yellow3);
        TileLocationState candidate = state(table(
            meld("RUN", red3, id(TileColor.RED, 4), id(TileColor.RED, 5)),
            meld("GROUP", red3, blue3, yellow3)
        ));
        String startBefore = start.toString();
        String candidateBefore = candidate.toString();

        validate(start, candidate);

        assertThat(start.toString()).isEqualTo(startBefore);
        assertThat(candidate.toString()).isEqualTo(candidateBefore);
    }

    @Test
    void rejectsMovingTileDirectlyFromPoolToTableEvenWhenTotalIntegrityIsPreserved() {
        TileId poolTile = id(TileColor.BLACK, 7);
        TableState startTable = table(meld("G7", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7)));
        TileLocationState start = state(startTable, id(TileColor.BLACK, 9));
        TableState candidateTable = table(meld(
            "G7", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7), poolTile
        ));
        TileLocationState candidate = state(candidateTable, id(TileColor.BLACK, 9));

        assertFailure(validate(start, candidate), RuleErrorCode.TILE_NOT_OWNED);
    }

    private ValidatedTurn success(TileLocationState start, TileLocationState candidate) {
        return ((ValidationSuccess<ValidatedTurn>) validate(start, candidate)).value();
    }

    private ValidationResult<ValidatedTurn> validate(TileLocationState start, TileLocationState candidate) {
        return validator.validate(new TurnValidationContext(
            GameMode.CLASSIC,
            P1,
            CATALOG,
            CANONICAL_IDS,
            start,
            candidate,
            true,
            new ClassicRulePolicy()
        ));
    }

    private static void assertFailure(ValidationResult<?> result, RuleErrorCode code) {
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(code);
    }
}
