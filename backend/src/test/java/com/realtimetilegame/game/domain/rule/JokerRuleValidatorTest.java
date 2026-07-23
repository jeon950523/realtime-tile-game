package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.JOKER_A;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.joker.JokerRuleValidator;
import com.realtimetilegame.game.domain.rule.joker.JokerValidationContext;
import com.realtimetilegame.game.domain.rule.joker.JokerValidationResult;
import com.realtimetilegame.game.domain.rule.meld.CompositeMeldValidator;
import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.List;

import org.junit.jupiter.api.Test;

class JokerRuleValidatorTest {
    private final JokerRuleValidator validator = new JokerRuleValidator();
    private final CompositeMeldValidator meldValidator = new CompositeMeldValidator();

    @Test
    void joker001RunValidatorCalculatesMiddleReplacement() {
        ValidatedMeld meld = validated(table(meld("RUN", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)))).get(0);
        assertThat(meld.jokerAssignments().get(JOKER_A).assignedNumber()).isEqualTo(4);
    }

    @Test
    void joker002GroupValidatorPreservesPossibleReplacementColors() {
        ValidatedMeld meld = validated(table(meld("GROUP", id(TileColor.RED, 7), id(TileColor.BLUE, 7), JOKER_A))).get(0);
        assertThat(meld.jokerAssignments().get(JOKER_A).replaceableColors())
            .containsExactlyInAnyOrder(TileColor.YELLOW, TileColor.BLACK);
    }

    @Test
    void joker003AcceptsExactReplacementAndSameTurnReuse() {
        Scenario scenario = exactRunReplacementScenario();
        JokerValidationResult result = success(scenario, true);
        assertThat(result.retrievedJokerIds()).containsExactly(JOKER_A);
    }

    @Test
    void joker004RejectsWrongReplacementEvenWhenFinalMeldsAreValid() {
        TableState startTable = table(meld("ORIGINAL", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)));
        TableState candidateTable = table(
            meld("JOKER-RUN", JOKER_A, id(TileColor.BLUE, 8), id(TileColor.BLUE, 9)),
            meld("LOW-RUN", id(TileColor.RED, 1), id(TileColor.RED, 2), id(TileColor.RED, 3)),
            meld("HIGH-RUN", id(TileColor.RED, 5), id(TileColor.RED, 6), id(TileColor.RED, 7)),
            meld("WRONG-GROUP", id(TileColor.BLUE, 4), id(TileColor.YELLOW, 4), id(TileColor.BLACK, 4))
        );
        RackState startRack = rack(
            id(TileColor.BLUE, 8), id(TileColor.BLUE, 9),
            id(TileColor.RED, 1), id(TileColor.RED, 2), id(TileColor.RED, 6), id(TileColor.RED, 7),
            id(TileColor.BLUE, 4), id(TileColor.YELLOW, 4), id(TileColor.BLACK, 4)
        );
        ValidationResult<JokerValidationResult> result = validate(
            new Scenario(startTable, candidateTable, startRack, RackState.empty()), true
        );
        assertFailure(result, RuleErrorCode.INVALID_JOKER_REPLACEMENT);
    }

    @Test
    void joker005RejectsRetrievalBeforeInitialMeldCompletion() {
        assertFailure(validate(exactRunReplacementScenario(), false), RuleErrorCode.JOKER_RETRIEVAL_NOT_ALLOWED);
    }

    @Test
    void joker006AcceptsRetrievedJokerReusedInDifferentValidMeld() {
        assertThat(success(exactRunReplacementScenario(), true).retrievedJokerIds()).contains(JOKER_A);
    }

    @Test
    void joker007RejectsKeepingRetrievedJokerInRack() {
        TableState startTable = table(meld("ORIGINAL", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)));
        TableState candidateTable = table(meld(
            "ORIGINAL", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5)
        ));
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 4)),
            rack(JOKER_A)
        );
        assertFailure(validate(scenario, true), RuleErrorCode.RETRIEVED_JOKER_NOT_REUSED);
    }

    @Test
    void joker008AcceptsSplittingJokerRunWhenFinalTableIsValid() {
        TableState startTable = table(meld(
            "LONG", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5), id(TileColor.RED, 6), id(TileColor.RED, 7)
        ));
        TableState candidateTable = table(
            meld("LEFT", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5)),
            meld("RIGHT", id(TileColor.RED, 6), id(TileColor.RED, 7), JOKER_A)
        );
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 4)),
            RackState.empty()
        );
        assertThat(success(scenario, true).retrievedJokerIds()).contains(JOKER_A);
    }

    @Test
    void joker009FailureDoesNotMoveJokerInEitherInputState() {
        Scenario scenario = new Scenario(
            table(meld("ORIGINAL", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5))),
            table(meld("REPLACED", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5))),
            rack(id(TileColor.RED, 4)),
            rack(JOKER_A)
        );
        String before = scenario.toString();

        validate(scenario, true);

        assertThat(scenario.toString()).isEqualTo(before);
        assertThat(scenario.startTable().allTileIds()).contains(JOKER_A);
        assertThat(scenario.candidateRack().tileIds()).contains(JOKER_A);
    }


    @Test
    void rejectsReuseThatIsNotBackedByValidatedCandidateMeld() {
        Scenario scenario = exactRunReplacementScenario();
        ValidationResult<JokerValidationResult> result = validator.validate(new JokerValidationContext(
            scenario.startTable(),
            scenario.candidateTable(),
            scenario.startRack(),
            scenario.candidateRack(),
            true,
            CATALOG,
            List.of()
        ));

        assertFailure(result, RuleErrorCode.RETRIEVED_JOKER_NOT_REUSED);
    }

    @Test
    void sameMeldIdCannotBypassJokerReplacement() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 5), JOKER_A, id(TileColor.RED, 7)
        ));
        TableState candidateTable = table(
            meld("M1", id(TileColor.BLUE, 10), JOKER_A, id(TileColor.BLUE, 12)),
            meld("G5", id(TileColor.RED, 5), id(TileColor.BLUE, 5), id(TileColor.YELLOW, 5)),
            meld("G7", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7))
        );
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(
                id(TileColor.BLUE, 10), id(TileColor.BLUE, 12),
                id(TileColor.BLUE, 5), id(TileColor.YELLOW, 5),
                id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7)
            ),
            RackState.empty()
        );

        assertFailure(validate(scenario, true), RuleErrorCode.INVALID_JOKER_REPLACEMENT);
    }

    @Test
    void sameMeldIdRoleChangeRequiresReplacement() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 5), JOKER_A, id(TileColor.RED, 7)
        ));
        TableState candidateTable = table(
            meld("M1", id(TileColor.BLUE, 10), JOKER_A, id(TileColor.BLUE, 12)),
            meld("REPLACEMENT", id(TileColor.RED, 5), id(TileColor.RED, 6), id(TileColor.RED, 7))
        );
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.BLUE, 10), id(TileColor.BLUE, 12), id(TileColor.RED, 6)),
            RackState.empty()
        );

        assertThat(success(scenario, true).retrievedJokerIds()).containsExactly(JOKER_A);
    }

    @Test
    void sameMeldIdContextChangeRequiresSameTurnReuse() {
        TileId red3A = id(TileColor.RED, 3, "A");
        TileId red5A = id(TileColor.RED, 5, "A");
        TileId red3B = id(TileColor.RED, 3, "B");
        TileId red5B = id(TileColor.RED, 5, "B");
        TableState startTable = table(meld("M1", red3A, JOKER_A, red5A));
        TableState candidateTable = table(
            meld("M1", red3B, JOKER_A, red5B),
            meld("REPLACEMENT", red3A, id(TileColor.RED, 4), red5A)
        );
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(red3B, red5B, id(TileColor.RED, 4)),
            RackState.empty()
        );

        assertThat(success(scenario, true).retrievedJokerIds()).containsExactly(JOKER_A);
    }

    @Test
    void sameMeldIdWithUnchangedJokerRoleIsNotFalsePositive() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)
        ));
        TableState candidateTable = table(meld(
            "M1", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5), id(TileColor.RED, 6)
        ));
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 6)),
            RackState.empty()
        );

        assertThat(success(scenario, true).retrievedJokerIds()).isEmpty();
    }

    @Test
    void prefixInsertionDoesNotRetrieveJokerWhenItsResolvedRoleAndRelativeContextStayUnchanged() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5), id(TileColor.RED, 6)
        ));
        TableState candidateTable = table(meld(
            "M1",
            id(TileColor.RED, 2), id(TileColor.RED, 3), JOKER_A,
            id(TileColor.RED, 5), id(TileColor.RED, 6)
        ));
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 2)),
            RackState.empty()
        );

        assertThat(success(scenario, true).retrievedJokerIds()).isEmpty();
    }

    @Test
    void prependingTenToElevenTwelveJokerKeepsJokerAsThirteenWithoutRetrieval() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 11), id(TileColor.RED, 12), JOKER_A
        ));
        TableState candidateTable = table(meld(
            "M1", id(TileColor.RED, 10), id(TileColor.RED, 11), id(TileColor.RED, 12), JOKER_A
        ));
        Scenario scenario = new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 10)),
            RackState.empty()
        );

        assertThat(success(scenario, true).retrievedJokerIds()).isEmpty();
    }

    @Test
    void renamingMeldIdAloneCannotChangeRuleOutcome() {
        TableState startTable = table(meld(
            "M1", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)
        ));
        Scenario unchangedId = new Scenario(
            startTable,
            table(meld("M1", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5))),
            RackState.empty(),
            RackState.empty()
        );
        Scenario renamedId = new Scenario(
            startTable,
            table(meld("RENAMED", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5))),
            RackState.empty(),
            RackState.empty()
        );

        assertThat(success(unchangedId, true).retrievedJokerIds()).isEmpty();
        assertThat(success(renamedId, true).retrievedJokerIds()).isEmpty();
    }

    private Scenario exactRunReplacementScenario() {
        TableState startTable = table(meld("ORIGINAL", id(TileColor.RED, 3), JOKER_A, id(TileColor.RED, 5)));
        TableState candidateTable = table(
            meld("ORIGINAL", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5)),
            meld("REUSED", JOKER_A, id(TileColor.BLUE, 8), id(TileColor.BLUE, 9))
        );
        return new Scenario(
            startTable,
            candidateTable,
            rack(id(TileColor.RED, 4), id(TileColor.BLUE, 8), id(TileColor.BLUE, 9)),
            RackState.empty()
        );
    }

    private JokerValidationResult success(Scenario scenario, boolean initialMeldCompleted) {
        return ((ValidationSuccess<JokerValidationResult>) validate(scenario, initialMeldCompleted)).value();
    }

    private ValidationResult<JokerValidationResult> validate(Scenario scenario, boolean initialMeldCompleted) {
        return validator.validate(new JokerValidationContext(
            scenario.startTable(),
            scenario.candidateTable(),
            scenario.startRack(),
            scenario.candidateRack(),
            initialMeldCompleted,
            CATALOG,
            validated(scenario.candidateTable())
        ));
    }

    private List<ValidatedMeld> validated(TableState table) {
        return table.melds().stream()
            .map(MeldCandidate::from)
            .map(candidate -> ((ValidationSuccess<ValidatedMeld>) meldValidator.validate(candidate, CATALOG)).value())
            .toList();
    }

    private static RackState rack(TileId... tileIds) {
        return new RackState(List.of(tileIds));
    }

    private static void assertFailure(ValidationResult<?> result, RuleErrorCode code) {
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code()).contains(code);
    }

    private record Scenario(
        TableState startTable,
        TableState candidateTable,
        RackState startRack,
        RackState candidateRack
    ) {
    }
}
