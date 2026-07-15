package com.realtimetilegame.game.domain.rule.model;

import java.util.Objects;

public record ValidationSuccess<T>(T value) implements ValidationResult<T> {
    public ValidationSuccess {
        Objects.requireNonNull(value, "value must not be null");
    }
}
