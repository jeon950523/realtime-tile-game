package com.realtimetilegame.game.domain.rule.model;

import java.util.Map;
import java.util.Objects;

public record RuleViolation(
    RuleErrorCode code,
    String message,
    Map<String, Object> details
) {
    public RuleViolation {
        code = Objects.requireNonNull(code, "code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static RuleViolation of(RuleErrorCode code, String message) {
        return new RuleViolation(code, message, Map.of());
    }
}
