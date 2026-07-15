package com.realtimetilegame.game.domain.rule.model;

import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ValidatedMeld(
    MeldId meldId,
    MeldType meldType,
    List<TileId> tileIds,
    Map<TileId, JokerAssignment> jokerAssignments,
    int score
) {
    public ValidatedMeld {
        meldId = Objects.requireNonNull(meldId, "meldId must not be null");
        meldType = Objects.requireNonNull(meldType, "meldType must not be null");
        tileIds = List.copyOf(Objects.requireNonNull(tileIds, "tileIds must not be null"));
        jokerAssignments = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
            Objects.requireNonNull(jokerAssignments, "jokerAssignments must not be null")
        ));
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
    }
}
