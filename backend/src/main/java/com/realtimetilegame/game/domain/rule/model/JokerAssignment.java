package com.realtimetilegame.game.domain.rule.model;

import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record JokerAssignment(
    TileId jokerTileId,
    int assignedNumber,
    TileColor resolvedColor,
    Set<TileColor> replaceableColors
) {
    public JokerAssignment {
        jokerTileId = Objects.requireNonNull(jokerTileId, "jokerTileId must not be null");
        if (assignedNumber < 1 || assignedNumber > 13) {
            throw new IllegalArgumentException("assignedNumber must be between 1 and 13");
        }
        resolvedColor = Objects.requireNonNull(resolvedColor, "resolvedColor must not be null");
        Objects.requireNonNull(replaceableColors, "replaceableColors must not be null");
        if (replaceableColors.isEmpty()) {
            throw new IllegalArgumentException("replaceableColors must not be empty");
        }
        replaceableColors = Collections.unmodifiableSet(EnumSet.copyOf(replaceableColors));
        if (!replaceableColors.contains(resolvedColor)) {
            throw new IllegalArgumentException("replaceableColors must contain resolvedColor");
        }
    }
}
