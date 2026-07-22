package com.realtimetilegame.game.domain.tile;

import java.util.Objects;

public record NumberTile(
    TileId id,
    TileColor color,
    int number
) implements Tile {
    public NumberTile {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(color, "color must not be null");
        if (number < 1 || number > 13) {
            throw new IllegalArgumentException("number must be between 1 and 13");
        }
    }
}
