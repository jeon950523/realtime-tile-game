package com.realtimetilegame.game.domain.rule.model;

import java.util.List;

public final class ValidationResults {
    private ValidationResults() {
    }

    public static <T> ValidationSuccess<T> success(T value) {
        return new ValidationSuccess<>(value);
    }

    public static <T> ValidationFailure<T> failure(RuleViolation violation) {
        return new ValidationFailure<>(List.of(violation));
    }

    public static <T> ValidationFailure<T> failure(List<RuleViolation> violations) {
        return new ValidationFailure<>(violations.size() <= ValidationFailure.MAX_VIOLATIONS
            ? violations
            : violations.subList(0, ValidationFailure.MAX_VIOLATIONS));
    }
}
