package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;

import java.util.Objects;
import java.util.Optional;

public record GameOutcomeCandidate(OutcomeType type, ParticipantId winner) {
    public GameOutcomeCandidate {
        type = Objects.requireNonNull(type, "type must not be null");
        if (type == OutcomeType.WIN && winner == null) {
            throw new IllegalArgumentException("WIN requires a winner");
        }
        if (type == OutcomeType.DRAW && winner != null) {
            throw new IllegalArgumentException("DRAW must not have a winner");
        }
    }

    public static GameOutcomeCandidate win(ParticipantId winner) {
        return new GameOutcomeCandidate(OutcomeType.WIN, Objects.requireNonNull(winner));
    }

    public static GameOutcomeCandidate draw() {
        return new GameOutcomeCandidate(OutcomeType.DRAW, null);
    }

    public Optional<ParticipantId> winnerOptional() {
        return Optional.ofNullable(winner);
    }
}
