package com.realtimetilegame.game.domain.rule.rearrangement;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.rule.turn.RackContributionValidator;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TableRearrangementValidator {
    private final RackContributionValidator contributionValidator;

    public TableRearrangementValidator() {
        this(new RackContributionValidator());
    }

    public TableRearrangementValidator(RackContributionValidator contributionValidator) {
        this.contributionValidator = Objects.requireNonNull(contributionValidator, "contributionValidator must not be null");
    }

    public ValidationResult<RearrangementResult> validate(RearrangementContext context) {
        if (!context.initialMeldCompleted() && !context.rulePolicy().allowsTableRearrangementBeforeInitialMeld()) {
            return ValidationResults.failure(RuleViolation.of(
                RuleErrorCode.TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD,
                "The public table cannot be rearranged before the initial meld is completed."
            ));
        }

        Set<TileId> startTableIds = new LinkedHashSet<>(context.turnStartTable().allTileIds());
        Set<TileId> candidateTableIds = new LinkedHashSet<>(context.candidateTable().allTileIds());
        Set<TileId> missingExistingTiles = new LinkedHashSet<>(startTableIds);
        missingExistingTiles.removeAll(candidateTableIds);
        if (!missingExistingTiles.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.MISSING_TILE,
                "Every tile from the turn-start table must remain on the candidate table.",
                Map.of("tileIds", missingExistingTiles.stream().map(TileId::value).toList())
            ));
        }

        Set<TileId> movedToRack = new LinkedHashSet<>(context.candidateRack().tileIds());
        movedToRack.retainAll(startTableIds);
        if (!movedToRack.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_OWNED,
                "A tile from the public table cannot be moved into the rack.",
                Map.of("tileIds", movedToRack.stream().map(TileId::value).toList())
            ));
        }

        if (context.validatedCandidateMelds().size() != context.candidateTable().melds().size()) {
            return ValidationResults.failure(RuleViolation.of(
                RuleErrorCode.INVALID_TABLE_LAYOUT,
                "Every candidate table meld must be valid."
            ));
        }

        ValidationResult<Set<TileId>> contribution = contributionValidator.validate(
            context.turnStartRack(),
            context.candidateRack(),
            context.turnStartTable(),
            context.candidateTable()
        );
        if (contribution.isFailure()) {
            return new ValidationFailure<>(((ValidationFailure<Set<TileId>>) contribution).violations());
        }
        return ValidationResults.success(new RearrangementResult(
            ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<Set<TileId>>) contribution).value()
        ));
    }
}
