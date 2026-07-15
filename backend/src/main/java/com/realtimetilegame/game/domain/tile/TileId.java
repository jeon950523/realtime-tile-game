package com.realtimetilegame.game.domain.tile;

public record TileId(String value) {
    public TileId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tileId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
