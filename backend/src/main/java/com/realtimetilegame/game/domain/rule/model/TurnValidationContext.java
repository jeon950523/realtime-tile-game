package com.realtimetilegame.game.domain.rule.model;

import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.game.domain.rule.policy.RulePolicy;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record TurnValidationContext(
    GameMode gameMode,
    ParticipantId currentParticipantId,
    TileCatalog tileCatalog,
    Set<TileId> canonicalTileIds,
    TileLocationState turnStartState,
    TileLocationState candidateState,
    boolean initialMeldCompleted,
    RulePolicy rulePolicy
) {
    public TurnValidationContext {
        gameMode = Objects.requireNonNull(gameMode, "gameMode must not be null");
        currentParticipantId = Objects.requireNonNull(currentParticipantId, "currentParticipantId must not be null");
        tileCatalog = Objects.requireNonNull(tileCatalog, "tileCatalog must not be null");
        Objects.requireNonNull(canonicalTileIds, "canonicalTileIds must not be null");
        canonicalTileIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(canonicalTileIds));
        turnStartState = Objects.requireNonNull(turnStartState, "turnStartState must not be null");
        candidateState = Objects.requireNonNull(candidateState, "candidateState must not be null");
        rulePolicy = Objects.requireNonNull(rulePolicy, "rulePolicy must not be null");

        if (rulePolicy.gameMode() != gameMode) {
            throw new IllegalArgumentException("gameMode and rulePolicy must describe the same mode");
        }
        if (!canonicalTileIds.equals(tileCatalog.tileIds())) {
            throw new IllegalArgumentException("canonicalTileIds must match the tile catalog exactly");
        }
        int activeParticipantCount = turnStartState.racks().size();
        if (activeParticipantCount < 2 || activeParticipantCount > 4) {
            throw new IllegalArgumentException("turn-start participant count must be between 2 and 4");
        }
        if (!turnStartState.racks().containsKey(currentParticipantId)) {
            throw new IllegalArgumentException("current participant must have a turn-start rack");
        }
    }
}
