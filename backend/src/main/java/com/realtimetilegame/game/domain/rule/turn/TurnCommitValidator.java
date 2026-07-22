package com.realtimetilegame.game.domain.rule.turn;

import com.realtimetilegame.game.domain.rule.initial.InitialMeldContext;
import com.realtimetilegame.game.domain.rule.initial.InitialMeldResult;
import com.realtimetilegame.game.domain.rule.initial.InitialMeldValidator;
import com.realtimetilegame.game.domain.rule.joker.JokerRuleValidator;
import com.realtimetilegame.game.domain.rule.joker.JokerValidationContext;
import com.realtimetilegame.game.domain.rule.meld.CompositeMeldValidator;
import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.TurnValidationContext;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidatedTurn;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.rule.rearrangement.RearrangementContext;
import com.realtimetilegame.game.domain.rule.rearrangement.TableRearrangementValidator;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TurnCommitValidator {
    private final CompositeMeldValidator meldValidator;
    private final TileIntegrityValidator integrityValidator;
    private final InitialMeldValidator initialMeldValidator;
    private final TurnCandidateLocationValidator locationValidator;
    private final RackContributionValidator contributionValidator;
    private final TableRearrangementValidator rearrangementValidator;
    private final JokerRuleValidator jokerRuleValidator;

    public TurnCommitValidator() {
        this(
            new CompositeMeldValidator(),
            new TileIntegrityValidator(),
            new InitialMeldValidator(),
            new TurnCandidateLocationValidator(),
            new RackContributionValidator(),
            new TableRearrangementValidator(),
            new JokerRuleValidator()
        );
    }

    public TurnCommitValidator(
        CompositeMeldValidator meldValidator,
        TileIntegrityValidator integrityValidator,
        InitialMeldValidator initialMeldValidator,
        TurnCandidateLocationValidator locationValidator,
        RackContributionValidator contributionValidator,
        TableRearrangementValidator rearrangementValidator,
        JokerRuleValidator jokerRuleValidator
    ) {
        this.meldValidator = Objects.requireNonNull(meldValidator, "meldValidator must not be null");
        this.integrityValidator = Objects.requireNonNull(integrityValidator, "integrityValidator must not be null");
        this.initialMeldValidator = Objects.requireNonNull(initialMeldValidator, "initialMeldValidator must not be null");
        this.locationValidator = Objects.requireNonNull(locationValidator, "locationValidator must not be null");
        this.contributionValidator = Objects.requireNonNull(contributionValidator, "contributionValidator must not be null");
        this.rearrangementValidator = Objects.requireNonNull(rearrangementValidator, "rearrangementValidator must not be null");
        this.jokerRuleValidator = Objects.requireNonNull(jokerRuleValidator, "jokerRuleValidator must not be null");
    }

    public ValidationResult<ValidatedTurn> validate(TurnValidationContext context) {
        Set<com.realtimetilegame.game.domain.state.MeldId> meldIds = new java.util.HashSet<>();
        for (MeldState meldState : context.candidateState().table().melds()) {
            if (!meldIds.add(meldState.meldId())) {
                return ValidationResults.failure(new RuleViolation(
                    RuleErrorCode.INVALID_TABLE_LAYOUT,
                    "The candidate table contains a duplicated meldId.",
                    java.util.Map.of("meldId", meldState.meldId().value())
                ));
            }
        }

        List<ValidatedMeld> validatedMelds = new ArrayList<>();
        List<RuleViolation> meldViolations = new ArrayList<>();
        for (MeldState meldState : context.candidateState().table().melds()) {
            ValidationResult<ValidatedMeld> result = meldValidator.validate(MeldCandidate.from(meldState), context.tileCatalog());
            if (result instanceof com.realtimetilegame.game.domain.rule.model.ValidationSuccess<ValidatedMeld> success) {
                validatedMelds.add(success.value());
            } else {
                ValidationFailure<ValidatedMeld> failure = (ValidationFailure<ValidatedMeld>) result;
                meldViolations.add(new RuleViolation(
                    RuleErrorCode.INVALID_TABLE_LAYOUT,
                    "The candidate table contains an invalid meld.",
                    java.util.Map.of(
                        "meldId", meldState.meldId().value(),
                        "errors", failure.violations().stream().map(v -> v.code().name()).toList()
                    )
                ));
                if (meldViolations.size() == ValidationFailure.MAX_VIOLATIONS) {
                    break;
                }
            }
        }
        if (!meldViolations.isEmpty()) {
            return ValidationResults.failure(meldViolations);
        }

        ValidationResult<TileIntegrityReport> integrity = integrityValidator.validate(
            context.candidateState(),
            context.canonicalTileIds()
        );
        if (integrity.isFailure()) {
            return new ValidationFailure<>(((ValidationFailure<TileIntegrityReport>) integrity).violations());
        }

        ValidationResult<Boolean> locationValidation = locationValidator.validate(
            context.currentParticipantId(),
            context.turnStartState(),
            context.candidateState()
        );
        if (locationValidation.isFailure()) {
            return new ValidationFailure<>(((ValidationFailure<Boolean>) locationValidation).violations());
        }

        RackState startRack = context.turnStartState().rackOf(context.currentParticipantId());
        RackState candidateRack = context.candidateState().rackOf(context.currentParticipantId());
        Set<TileId> rackToTableTiles;
        int initialMeldScore = 0;
        boolean completedAfterValidation;

        boolean requiresInitialPath = context.rulePolicy().requiresInitialMeld() && !context.initialMeldCompleted();
        if (requiresInitialPath) {
            ValidationResult<InitialMeldResult> initial = initialMeldValidator.validate(new InitialMeldContext(
                context.currentParticipantId(),
                startRack,
                candidateRack,
                context.turnStartState().table(),
                context.candidateState().table(),
                validatedMelds,
                false,
                context.rulePolicy()
            ));
            if (initial.isFailure()) {
                return new ValidationFailure<>(((ValidationFailure<InitialMeldResult>) initial).violations());
            }
            InitialMeldResult value = ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<InitialMeldResult>) initial).value();
            initialMeldScore = value.totalScore();
            completedAfterValidation = value.completed();
            rackToTableTiles = difference(startRack.tileIds(), candidateRack.tileIds());
        } else {
            ValidationResult<Set<TileId>> contribution = contributionValidator.validate(
                startRack,
                candidateRack,
                context.turnStartState().table(),
                context.candidateState().table()
            );
            if (contribution.isFailure()) {
                return new ValidationFailure<>(((ValidationFailure<Set<TileId>>) contribution).violations());
            }
            rackToTableTiles = ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<Set<TileId>>) contribution).value();

            ValidationResult<?> rearrangement = rearrangementValidator.validate(new RearrangementContext(
                context.turnStartState().table(),
                context.candidateState().table(),
                startRack,
                candidateRack,
                context.initialMeldCompleted(),
                context.rulePolicy(),
                validatedMelds
            ));
            if (rearrangement.isFailure()) {
                return new ValidationFailure<>(((ValidationFailure<?>) rearrangement).violations());
            }

            ValidationResult<?> jokerResult = jokerRuleValidator.validate(new JokerValidationContext(
                context.turnStartState().table(),
                context.candidateState().table(),
                startRack,
                candidateRack,
                context.initialMeldCompleted() || !context.rulePolicy().requiresInitialMeld(),
                context.tileCatalog(),
                validatedMelds
            ));
            if (jokerResult.isFailure()) {
                return new ValidationFailure<>(((ValidationFailure<?>) jokerResult).violations());
            }
            completedAfterValidation = context.initialMeldCompleted() || !context.rulePolicy().requiresInitialMeld();
        }

        return ValidationResults.success(new ValidatedTurn(
            context.candidateState().table(),
            candidateRack,
            validatedMelds,
            rackToTableTiles,
            initialMeldScore,
            completedAfterValidation,
            candidateRack.isEmpty()
        ));
    }

    private static Set<TileId> difference(List<TileId> start, List<TileId> candidate) {
        Set<TileId> result = new LinkedHashSet<>(start);
        result.removeAll(candidate);
        return java.util.Collections.unmodifiableSet(result);
    }
}
