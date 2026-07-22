package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_B;
import static com.realtimetilegame.game.support.RuleTestFixtures.candidate;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.meld.RunValidator;
import com.realtimetilegame.game.domain.rule.model.JokerAssignment;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RunValidatorTest {
    private final RunValidator validator = new RunValidator();

    @Test
    void run001AcceptsThreeConsecutiveTilesInSubmittedOrder() {
        ValidatedMeld meld = success(id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5));
        assertThat(meld.score()).isEqualTo(12);
    }

    @Test
    void run002AcceptsFullOneToThirteenRun() {
        List<TileId> ids = new ArrayList<>();
        for (int number = 1; number <= 13; number++) {
            ids.add(id(TileColor.RED, number));
        }
        assertThat(validator.validate(candidate("RUN", ids.toArray(TileId[]::new)), CATALOG).isSuccess()).isTrue();
    }

    @Test
    void run003RejectsTwoTiles() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 3), id(TileColor.RED, 4));
    }

    @Test
    void run004RejectsMixedColors() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 3), id(TileColor.BLUE, 4), id(TileColor.RED, 5));
    }

    @Test
    void run005RejectsMissingNumber() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 3), id(TileColor.RED, 5), id(TileColor.RED, 6));
    }

    @Test
    void run006RejectsDuplicatedNumberEvenWithDistinctPhysicalTiles() {
        assertFailure(
            RuleErrorCode.INVALID_RUN,
            id(TileColor.RED, 3, "A"),
            id(TileColor.RED, 3, "B"),
            id(TileColor.RED, 4)
        );
    }

    @Test
    void run007RejectsThirteenToOneWrap() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 12), id(TileColor.RED, 13), id(TileColor.RED, 1));
    }

    @Test
    void run008AssignsMiddleJokerFromMeldPosition() {
        JokerAssignment assignment = success(id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5))
            .jokerAssignments().get(JOKER_A);
        assertThat(assignment.assignedNumber()).isEqualTo(4);
        assertThat(assignment.resolvedColor()).isEqualTo(TileColor.RED);
    }

    @Test
    void run009AssignsLeadingJokerFromMeldPosition() {
        assertThat(success(JOKER_A, id(TileColor.RED, 4), id(TileColor.RED, 5))
            .jokerAssignments().get(JOKER_A).assignedNumber()).isEqualTo(3);
    }

    @Test
    void run010AssignsTrailingJokerAsThirteen() {
        assertThat(success(id(TileColor.RED, 11), id(TileColor.RED, 12), JOKER_A)
            .jokerAssignments().get(JOKER_A).assignedNumber()).isEqualTo(13);
    }

    @Test
    void run011RejectsJokersThatWouldContinueBeyondThirteen() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 12), JOKER_A, JOKER_B);
    }

    @Test
    void run012RejectsJokerOnlyMeld() {
        TileId thirdJoker = new TileId("JOKER-C");
        com.realtimetilegame.game.domain.tile.TileCatalog catalog = new com.realtimetilegame.game.domain.tile.TileCatalog(List.of(
            CATALOG.get(JOKER_A), CATALOG.get(JOKER_B), new com.realtimetilegame.game.domain.tile.JokerTile(thirdJoker)
        ));
        ValidationResult<ValidatedMeld> result = validator.validate(candidate("RUN", JOKER_A, JOKER_B, thirdJoker), catalog);
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(RuleErrorCode.INVALID_RUN);
    }

    @Test
    void run013RejectsSamePhysicalTileTwice() {
        TileId tileId = id(TileColor.RED, 3);
        assertFailure(RuleErrorCode.DUPLICATED_TILE, tileId, tileId, id(TileColor.RED, 4));
    }

    @Test
    void doesNotSortSubmittedRunOrder() {
        assertFailure(RuleErrorCode.INVALID_RUN, id(TileColor.RED, 5), id(TileColor.RED, 3), id(TileColor.RED, 4));
    }

    @Test
    void repeatedValidationIsDeterministic() {
        var candidate = candidate("RUN", JOKER_A, id(TileColor.RED, 4), id(TileColor.RED, 5));
        assertThat(validator.validate(candidate, CATALOG)).isEqualTo(validator.validate(candidate, CATALOG));
    }

    private ValidatedMeld success(TileId... tileIds) {
        return ((ValidationSuccess<ValidatedMeld>) validator.validate(candidate("RUN", tileIds), CATALOG)).value();
    }

    private void assertFailure(RuleErrorCode expectedCode, TileId... tileIds) {
        ValidationResult<ValidatedMeld> result = validator.validate(candidate("RUN", tileIds), CATALOG);
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(expectedCode);
    }
}
