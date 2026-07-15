package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_B;
import static com.realtimetilegame.game.support.RuleTestFixtures.candidate;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.meld.GroupValidator;
import com.realtimetilegame.game.domain.rule.model.JokerAssignment;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.tile.JokerTile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GroupValidatorTest {
    private final GroupValidator validator = new GroupValidator();

    @Test
    void group001AcceptsThreeDifferentColors() {
        assertThat(success(id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7)).score()).isEqualTo(21);
    }

    @Test
    void group002AcceptsFourDifferentColors() {
        assertThat(success(
            id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7), id(TileColor.BLACK, 7)
        ).score()).isEqualTo(28);
    }

    @Test
    void group003RejectsTwoTiles() {
        assertFailure(RuleErrorCode.INVALID_GROUP, id(TileColor.RED, 7), id(TileColor.BLUE, 7));
    }

    @Test
    void group004RejectsDuplicatedColor() {
        assertFailure(
            RuleErrorCode.INVALID_GROUP,
            id(TileColor.RED, 7, "A"), id(TileColor.RED, 7, "B"), id(TileColor.BLUE, 7)
        );
    }

    @Test
    void group005RejectsMixedNumbers() {
        assertFailure(RuleErrorCode.INVALID_GROUP, id(TileColor.RED, 7), id(TileColor.BLUE, 8), id(TileColor.BLACK, 7));
    }

    @Test
    void group006RejectsFiveTiles() {
        assertFailure(
            RuleErrorCode.INVALID_GROUP,
            id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7), id(TileColor.BLACK, 7), JOKER_A
        );
    }

    @Test
    void group007AssignsOneJokerAndPreservesAllReplaceableColors() {
        JokerAssignment assignment = success(id(TileColor.RED, 7), id(TileColor.BLUE, 7), JOKER_A)
            .jokerAssignments().get(JOKER_A);
        assertThat(assignment.assignedNumber()).isEqualTo(7);
        assertThat(assignment.resolvedColor()).isEqualTo(TileColor.YELLOW);
        assertThat(assignment.replaceableColors()).containsExactlyInAnyOrder(TileColor.YELLOW, TileColor.BLACK);
    }

    @Test
    void group008AssignsTwoJokersDeterministically() {
        ValidatedMeld meld = success(id(TileColor.RED, 7), JOKER_A, JOKER_B);
        assertThat(meld.jokerAssignments().get(JOKER_A).resolvedColor()).isEqualTo(TileColor.BLUE);
        assertThat(meld.jokerAssignments().get(JOKER_B).resolvedColor()).isEqualTo(TileColor.YELLOW);
        assertThat(meld.jokerAssignments().values())
            .allSatisfy(assignment -> assertThat(assignment.replaceableColors())
                .containsExactlyInAnyOrder(TileColor.BLUE, TileColor.YELLOW, TileColor.BLACK));
    }

    @Test
    void group009RejectsJokerOnlyMeld() {
        TileId thirdJoker = new TileId("JOKER-C");
        TileCatalog catalog = new TileCatalog(List.of(
            CATALOG.get(JOKER_A), CATALOG.get(JOKER_B), new JokerTile(thirdJoker)
        ));
        ValidationResult<ValidatedMeld> result = validator.validate(candidate("GROUP", JOKER_A, JOKER_B, thirdJoker), catalog);
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(RuleErrorCode.INVALID_GROUP);
    }

    @Test
    void group010RejectsSamePhysicalTileTwice() {
        TileId tileId = id(TileColor.RED, 7);
        assertFailure(RuleErrorCode.DUPLICATED_TILE, tileId, tileId, id(TileColor.BLUE, 7));
    }

    @Test
    void repeatedGroupValidationIsDeterministic() {
        var candidate = candidate("GROUP", id(TileColor.RED, 7), JOKER_A, JOKER_B);
        assertThat(validator.validate(candidate, CATALOG)).isEqualTo(validator.validate(candidate, CATALOG));
    }

    @Test
    void jokerAssignmentOrderFollowsCandidateJokerOrder() {
        ValidatedMeld meld = success(id(TileColor.RED, 7), JOKER_B, JOKER_A);

        assertThat(meld.jokerAssignments().keySet()).containsExactly(JOKER_B, JOKER_A);
    }

    private ValidatedMeld success(TileId... tileIds) {
        return ((ValidationSuccess<ValidatedMeld>) validator.validate(candidate("GROUP", tileIds), CATALOG)).value();
    }

    private void assertFailure(RuleErrorCode expectedCode, TileId... tileIds) {
        ValidationResult<ValidatedMeld> result = validator.validate(candidate("GROUP", tileIds), CATALOG);
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(expectedCode);
    }
}
