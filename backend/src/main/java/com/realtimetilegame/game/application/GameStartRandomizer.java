package com.realtimetilegame.game.application;

import java.util.List;

import com.realtimetilegame.game.domain.tile.Tile;

public interface GameStartRandomizer {
    List<Tile> shuffledTiles(List<Tile> tiles);
    int firstPlayerIndex(int playerCount);
}
