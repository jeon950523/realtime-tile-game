package com.realtimetilegame.game.domain.tile;

import java.util.Objects;

public record JokerTile(TileId id) implements Tile {
    public JokerTile {
        Objects.requireNonNull(id, "id must not be null");
    }
}
