package com.realtimetilegame.game.domain.tile;

public sealed interface Tile permits NumberTile, JokerTile {
    TileId id();
}
