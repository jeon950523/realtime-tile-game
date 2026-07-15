package com.realtimetilegame.game.domain.rule.turn;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.TileLocationState;

import java.util.Map;

public final class TurnCandidateLocationValidator {
    public ValidationResult<Boolean> validate(
        ParticipantId currentParticipantId,
        TileLocationState turnStartState,
        TileLocationState candidateState
    ) {
        if (!turnStartState.tilePool().equals(candidateState.tilePool())) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_OWNED,
                "A turn commit candidate cannot change the public tile pool.",
                Map.of("location", "TILE_POOL")
            ));
        }
        if (!turnStartState.racks().keySet().equals(candidateState.racks().keySet())) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_OWNED,
                "A turn commit candidate cannot add or remove participant racks.",
                Map.of("location", "RACKS")
            ));
        }
        for (Map.Entry<ParticipantId, com.realtimetilegame.game.domain.state.RackState> entry
            : turnStartState.racks().entrySet()) {
            if (!entry.getKey().equals(currentParticipantId)
                && !entry.getValue().equals(candidateState.racks().get(entry.getKey()))) {
                return ValidationResults.failure(new RuleViolation(
                    RuleErrorCode.TILE_NOT_OWNED,
                    "A player cannot change another participant's rack.",
                    Map.of("participantId", entry.getKey().value())
                ));
            }
        }
        return ValidationResults.success(Boolean.TRUE);
    }
}
