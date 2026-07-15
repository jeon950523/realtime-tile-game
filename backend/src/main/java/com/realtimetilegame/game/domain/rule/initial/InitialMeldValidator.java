package com.realtimetilegame.game.domain.rule.initial;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.rule.turn.RackContributionValidator;
import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class InitialMeldValidator {
    private final RackContributionValidator contributionValidator;

    public InitialMeldValidator() {
        this(new RackContributionValidator());
    }

    public InitialMeldValidator(RackContributionValidator contributionValidator) {
        this.contributionValidator = Objects.requireNonNull(contributionValidator, "contributionValidator must not be null");
    }

    public ValidationResult<InitialMeldResult> validate(InitialMeldContext context) {
        if (context.initialMeldCompleted()) {
            return ValidationResults.success(new InitialMeldResult(0, true));
        }
        if (!context.rulePolicy().requiresInitialMeld()) {
            return ValidationResults.success(new InitialMeldResult(0, true));
        }
        if (!existingTableIsUnchanged(context)) {
            return ValidationResults.failure(RuleViolation.of(
                RuleErrorCode.TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD,
                "Existing table melds cannot be changed during the initial meld turn."
            ));
        }

        ValidationResult<Set<TileId>> contributionResult = contributionValidator.validate(
            context.turnStartRack(),
            context.candidateRack(),
            context.turnStartTable(),
            context.candidateTable()
        );
        if (contributionResult.isFailure()) {
            return new ValidationFailure<>(((ValidationFailure<Set<TileId>>) contributionResult).violations());
        }

        Set<MeldId> existingMeldIds = context.turnStartTable().melds().stream()
            .map(MeldState::meldId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ValidatedMeld> newlyAddedMelds = context.candidateMelds().stream()
            .filter(meld -> !existingMeldIds.contains(meld.meldId()))
            .toList();
        int score = newlyAddedMelds.stream().mapToInt(ValidatedMeld::score).sum();
        if (score < context.rulePolicy().requiredInitialMeldScore()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.INITIAL_MELD_SCORE_TOO_LOW,
                "The initial meld score is below the required threshold.",
                Map.of(
                    "actualScore", score,
                    "requiredScore", context.rulePolicy().requiredInitialMeldScore()
                )
            ));
        }

        Set<TileId> contributed = ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<Set<TileId>>) contributionResult).value();
        Set<TileId> newMeldTileIds = newlyAddedMelds.stream()
            .flatMap(meld -> meld.tileIds().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!newMeldTileIds.equals(contributed)) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.INVALID_TABLE_LAYOUT,
                "The initial meld must consist only of tiles contributed from the turn-start rack.",
                Map.of(
                    "contributedTileIds", contributed.stream().map(TileId::value).toList(),
                    "newMeldTileIds", newMeldTileIds.stream().map(TileId::value).toList()
                )
            ));
        }

        return ValidationResults.success(new InitialMeldResult(score, true));
    }

    private static boolean existingTableIsUnchanged(InitialMeldContext context) {
        Map<MeldId, MeldState> candidateById = new LinkedHashMap<>();
        for (MeldState candidateMeld : context.candidateTable().melds()) {
            if (candidateById.putIfAbsent(candidateMeld.meldId(), candidateMeld) != null) {
                return false;
            }
        }
        for (MeldState startMeld : context.turnStartTable().melds()) {
            if (!startMeld.equals(candidateById.get(startMeld.meldId()))) {
                return false;
            }
        }
        return true;
    }
}
