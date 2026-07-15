package com.realtimetilegame.game.domain.tile;

import java.util.ArrayList;
import java.util.List;

public final class TileSetFactory {
    public static final int STANDARD_TILE_COUNT = 106;

    private TileSetFactory() {
    }

    public static List<Tile> createStandardSet() {
        List<Tile> tiles = new ArrayList<>(STANDARD_TILE_COUNT);
        for (TileColor color : TileColor.values()) {
            for (int number = 1; number <= 13; number++) {
                tiles.add(numberTile(color, number, "A"));
                tiles.add(numberTile(color, number, "B"));
            }
        }
        tiles.add(new JokerTile(new TileId("JOKER-A")));
        tiles.add(new JokerTile(new TileId("JOKER-B")));
        return List.copyOf(tiles);
    }

    private static NumberTile numberTile(TileColor color, int number, String copy) {
        String id = "%s-%02d-%s".formatted(color.name(), number, copy);
        return new NumberTile(new TileId(id), color, number);
    }
}
