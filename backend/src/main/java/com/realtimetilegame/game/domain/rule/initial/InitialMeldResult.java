package com.realtimetilegame.game.domain.rule.initial;

public record InitialMeldResult(int totalScore, boolean completed) {
    public InitialMeldResult {
        if (totalScore < 0) {
            throw new IllegalArgumentException("totalScore must not be negative");
        }
    }
}
