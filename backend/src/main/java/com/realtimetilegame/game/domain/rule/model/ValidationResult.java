package com.realtimetilegame.game.domain.rule.model;

public sealed interface ValidationResult<T> permits ValidationSuccess, ValidationFailure {
    default boolean isSuccess() {
        return this instanceof ValidationSuccess<?>;
    }

    default boolean isFailure() {
        return this instanceof ValidationFailure<?>;
    }
}
