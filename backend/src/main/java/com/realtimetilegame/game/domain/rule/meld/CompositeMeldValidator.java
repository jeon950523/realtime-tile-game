package com.realtimetilegame.game.domain.rule.meld;

import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.tile.TileCatalog;

import java.util.Map;
import java.util.Objects;

public final class CompositeMeldValidator implements MeldValidator {
    private final RunValidator runValidator;
    private final GroupValidator groupValidator;

    public CompositeMeldValidator() {
        this(new RunValidator(), new GroupValidator());
    }

    public CompositeMeldValidator(RunValidator runValidator, GroupValidator groupValidator) {
        this.runValidator = Objects.requireNonNull(runValidator, "runValidator must not be null");
        this.groupValidator = Objects.requireNonNull(groupValidator, "groupValidator must not be null");
    }

    @Override
    public ValidationResult<ValidatedMeld> validate(MeldCandidate candidate, TileCatalog tileCatalog) {
        ValidationResult<ValidatedMeld> runResult = runValidator.validate(candidate, tileCatalog);
        if (runResult.isSuccess()) {
            return runResult;
        }
        ValidationResult<ValidatedMeld> groupResult = groupValidator.validate(candidate, tileCatalog);
        if (groupResult.isSuccess()) {
            return groupResult;
        }
        ValidationFailure<ValidatedMeld> runFailure = (ValidationFailure<ValidatedMeld>) runResult;
        ValidationFailure<ValidatedMeld> groupFailure = (ValidationFailure<ValidatedMeld>) groupResult;
        return ValidationResults.failure(new RuleViolation(
            RuleErrorCode.INVALID_MELD,
            "The submitted meld is neither a valid run nor a valid group.",
            Map.of(
                "runErrors", runFailure.violations().stream().map(v -> v.code().name()).toList(),
                "groupErrors", groupFailure.violations().stream().map(v -> v.code().name()).toList()
            )
        ));
    }
}
