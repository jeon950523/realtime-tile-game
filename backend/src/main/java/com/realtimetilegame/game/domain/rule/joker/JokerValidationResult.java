package com.realtimetilegame.game.domain.rule.joker;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.Set;

public record JokerValidationResult(Set<TileId> retrievedJokerIds) {
    public JokerValidationResult {
        retrievedJokerIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(retrievedJokerIds));
    }
}
