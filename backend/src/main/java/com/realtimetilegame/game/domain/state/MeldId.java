package com.realtimetilegame.game.domain.state;

public record MeldId(String value) {
    public MeldId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("meldId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
