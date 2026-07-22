package com.realtimetilegame.game.domain.state;

public record ParticipantId(String value) {
    public ParticipantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("participantId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
