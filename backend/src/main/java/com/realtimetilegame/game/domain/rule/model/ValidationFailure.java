package com.realtimetilegame.game.domain.rule.model;

import java.util.List;
import java.util.Objects;

public record ValidationFailure<T>(List<RuleViolation> violations) implements ValidationResult<T> {
    public static final int MAX_VIOLATIONS = 5;

    public ValidationFailure {
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("violations must not be empty");
        }
        if (violations.size() > MAX_VIOLATIONS) {
            throw new IllegalArgumentException("violations must not exceed " + MAX_VIOLATIONS);
        }
    }
}
