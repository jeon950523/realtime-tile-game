package com.realtimetilegame.game.domain.rule.rearrangement;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.Set;

public record RearrangementResult(Set<TileId> contributedTileIds) {
    public RearrangementResult {
        contributedTileIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(contributedTileIds));
    }
}
