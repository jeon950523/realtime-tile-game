package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.initial.InitialMeldContext;
import com.realtimetilegame.game.domain.rule.initial.InitialMeldResult;
import com.realtimetilegame.game.domain.rule.initial.InitialMeldValidator;
import com.realtimetilegame.game.domain.rule.meld.CompositeMeldValidator;
import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.SpeedRulePolicy;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class InitialMeldValidatorTest {
    private final InitialMeldValidator validator = new InitialMeldValidator();
    private final CompositeMeldValidator meldValidator = new CompositeMeldValidator();

    @Test
    void init001AcceptsExactlyThirtyPoints() {
        MeldState group = meld("G10", id(TileColor.RED, 10), id(TileColor.BLUE, 10), id(TileColor.YELLOW, 10));
        assertThat(success(TableState.empty(), table(group), rack(group.tileIds()), RackState.empty(), false, false).totalScore())
            .isEqualTo(30);
    }

    @Test
    void init002RejectsTwentyNinePoints() {
        MeldState run = meld("R", id(TileColor.RED, 5), id(TileColor.RED, 6), id(TileColor.RED, 7), id(TileColor.RED, 8));
        MeldState group = meld("G", id(TileColor.RED, 1), id(TileColor.BLUE, 1), id(TileColor.YELLOW, 1));
        ValidationResult<InitialMeldResult> result = validate(
            TableState.empty(), table(run, group), rack(concat(run.tileIds(), group.tileIds())), RackState.empty(), false, false
        );
        assertFailure(result, RuleErrorCode.INITIAL_MELD_SCORE_TOO_LOW);
    }

    @Test
    void init003AcceptsThirtyOnePoints() {
        MeldState run = meld("R", id(TileColor.RED, 4), id(TileColor.RED, 5), id(TileColor.RED, 6));
        MeldState group = meld("G", id(TileColor.RED, 4, "B"), id(TileColor.BLUE, 4), id(TileColor.YELLOW, 4), id(TileColor.BLACK, 4));
        assertThat(success(
            TableState.empty(), table(run, group), rack(concat(run.tileIds(), group.tileIds())), RackState.empty(), false, false
        ).totalScore()).isEqualTo(31);
    }

    @Test
    void init004SumsMultipleMeldsToThirty() {
        MeldState run = meld("R", id(TileColor.RED, 7), id(TileColor.RED, 8), id(TileColor.RED, 9));
        MeldState group = meld("G", id(TileColor.BLUE, 2), id(TileColor.YELLOW, 2), id(TileColor.BLACK, 2));
        assertThat(success(
            TableState.empty(), table(run, group), rack(concat(run.tileIds(), group.tileIds())), RackState.empty(), false, false
        ).totalScore()).isEqualTo(30);
    }

    @Test
    void init005RejectsUsingExistingTableTile() {
        MeldState start = meld("START", id(TileColor.RED, 9), id(TileColor.RED, 10), id(TileColor.RED, 11));
        MeldState changed = meld("START", id(TileColor.RED, 9), id(TileColor.RED, 10), id(TileColor.RED, 11), id(TileColor.RED, 12));
        ValidationResult<InitialMeldResult> result = validate(
            table(start), table(changed), rack(id(TileColor.RED, 12)), RackState.empty(), false, false
        );
        assertFailure(result, RuleErrorCode.TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD);
    }

    @Test
    void init006RejectsRearrangingExistingTableEvenWhenTilesArePreserved() {
        MeldState start = meld("START", id(TileColor.RED, 9), id(TileColor.RED, 10), id(TileColor.RED, 11));
        MeldState renamed = meld("RENAMED", start.tileIds().toArray(TileId[]::new));
        ValidationResult<InitialMeldResult> result = validate(
            table(start), table(renamed), RackState.empty(), RackState.empty(), false, false
        );
        assertFailure(result, RuleErrorCode.TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD);
    }

    @Test
    void init007AcceptsJokerInInitialMeldAboveThirty() {
        MeldState run = meld("R", id(TileColor.RED, 10), JOKER_A, id(TileColor.RED, 12));
        assertThat(success(TableState.empty(), table(run), rack(run.tileIds()), RackState.empty(), false, false).totalScore())
            .isEqualTo(33);
    }

    @Test
    void init008UsesAssignedJokerNumberInScore() {
        MeldState run = meld("R", id(TileColor.RED, 10), JOKER_A, id(TileColor.RED, 12));
        assertThat(validated(table(run)).get(0).score()).isEqualTo(33);
    }

    @Test
    void init009MarksInitialMeldCompletedOnSuccess() {
        MeldState group = meld("G10", id(TileColor.RED, 10), id(TileColor.BLUE, 10), id(TileColor.YELLOW, 10));
        assertThat(success(TableState.empty(), table(group), rack(group.tileIds()), RackState.empty(), false, false).completed())
            .isTrue();
    }

    @Test
    void init010FailureDoesNotMutateInputStates() {
        MeldState run = meld("R", id(TileColor.RED, 5), id(TileColor.RED, 6), id(TileColor.RED, 7), id(TileColor.RED, 8));
        MeldState group = meld("G", id(TileColor.RED, 1), id(TileColor.BLUE, 1), id(TileColor.YELLOW, 1));
        TableState startTable = TableState.empty();
        TableState candidateTable = table(run, group);
        RackState startRack = rack(concat(run.tileIds(), group.tileIds()));
        RackState candidateRack = RackState.empty();
        String before = List.of(startTable, candidateTable, startRack, candidateRack).toString();

        validate(startTable, candidateTable, startRack, candidateRack, false, false);

        assertThat(List.of(startTable, candidateTable, startRack, candidateRack).toString()).isEqualTo(before);
    }

    @Test
    void init011AcceptsNewMeldsBeforeExistingMeldsInCandidateOrder() {
        MeldState existing = meld("EXISTING", id(TileColor.BLACK, 1), id(TileColor.BLACK, 2), id(TileColor.BLACK, 3));
        MeldState run = meld("RUN", id(TileColor.RED, 7), id(TileColor.RED, 8), id(TileColor.RED, 9));
        MeldState group = meld("GROUP", id(TileColor.BLUE, 2), id(TileColor.YELLOW, 2), id(TileColor.BLACK, 2, "B"));
        List<TileId> contributed = concat(run.tileIds(), group.tileIds());

        InitialMeldResult result = success(
            table(existing), table(run, group, existing), rack(contributed), RackState.empty(), false, false
        );

        assertThat(result.totalScore()).isEqualTo(30);
        assertThat(result.completed()).isTrue();
    }

    @Test
    void init012AcceptsNewMeldBetweenExistingMeldsInCandidateOrder() {
        MeldState firstExisting = meld("FIRST", id(TileColor.BLACK, 1), id(TileColor.BLACK, 2), id(TileColor.BLACK, 3));
        MeldState secondExisting = meld("SECOND", id(TileColor.BLUE, 5), id(TileColor.BLUE, 6), id(TileColor.BLUE, 7));
        MeldState run = meld("RUN", id(TileColor.RED, 7), id(TileColor.RED, 8), id(TileColor.RED, 9));
        MeldState group = meld("GROUP", id(TileColor.BLUE, 2), id(TileColor.YELLOW, 2), id(TileColor.BLACK, 2, "B"));
        List<TileId> contributed = concat(run.tileIds(), group.tileIds());

        InitialMeldResult result = success(
            table(firstExisting, secondExisting),
            table(firstExisting, run, group, secondExisting),
            rack(contributed), RackState.empty(), false, false
        );

        assertThat(result.totalScore()).isEqualTo(30);
        assertThat(result.completed()).isTrue();
    }

    @Test
    void init013RejectsChangedExistingMeldEvenWhenCandidateOrderChanges() {
        MeldState existing = meld("EXISTING", id(TileColor.BLACK, 1), id(TileColor.BLACK, 2), id(TileColor.BLACK, 3));
        MeldState changed = meld("EXISTING", id(TileColor.BLACK, 2), id(TileColor.BLACK, 3), id(TileColor.BLACK, 4));
        MeldState run = meld("RUN", id(TileColor.RED, 7), id(TileColor.RED, 8), id(TileColor.RED, 9));
        MeldState group = meld("GROUP", id(TileColor.BLUE, 2), id(TileColor.YELLOW, 2), id(TileColor.BLACK, 2, "B"));
        List<TileId> contributed = concat(run.tileIds(), group.tileIds());

        ValidationResult<InitialMeldResult> result = validate(
            table(existing), table(run, group, changed), rack(contributed), RackState.empty(), false, false
        );

        assertFailure(result, RuleErrorCode.TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD);
    }

    @Test
    void init014SpeedPolicySkipsInitialMeldRequirement() {
        ValidationResult<InitialMeldResult> result = validate(
            TableState.empty(), TableState.empty(), RackState.empty(), RackState.empty(), false, true
        );
        assertThat(result).isInstanceOf(ValidationSuccess.class);
        assertThat(((ValidationSuccess<InitialMeldResult>) result).value())
            .isEqualTo(new InitialMeldResult(0, true));
    }

    private InitialMeldResult success(
        TableState startTable,
        TableState candidateTable,
        RackState startRack,
        RackState candidateRack,
        boolean alreadyCompleted,
        boolean speed
    ) {
        return ((ValidationSuccess<InitialMeldResult>) validate(
            startTable, candidateTable, startRack, candidateRack, alreadyCompleted, speed
        )).value();
    }

    private ValidationResult<InitialMeldResult> validate(
        TableState startTable,
        TableState candidateTable,
        RackState startRack,
        RackState candidateRack,
        boolean alreadyCompleted,
        boolean speed
    ) {
        return validator.validate(new InitialMeldContext(
            P1,
            startRack,
            candidateRack,
            startTable,
            candidateTable,
            validated(candidateTable),
            alreadyCompleted,
            speed ? new SpeedRulePolicy() : new ClassicRulePolicy()
        ));
    }

    private List<ValidatedMeld> validated(TableState table) {
        return table.melds().stream()
            .map(MeldCandidate::from)
            .map(candidate -> ((ValidationSuccess<ValidatedMeld>) meldValidator.validate(candidate, CATALOG)).value())
            .toList();
    }

    private static RackState rack(TileId... ids) {
        return new RackState(List.of(ids));
    }

    private static RackState rack(List<TileId> ids) {
        return new RackState(ids);
    }

    @SafeVarargs
    private static List<TileId> concat(List<TileId>... lists) {
        List<TileId> result = new ArrayList<>();
        Arrays.stream(lists).forEach(result::addAll);
        return result;
    }

    private static void assertFailure(ValidationResult<?> result, RuleErrorCode code) {
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(code);
    }
}
