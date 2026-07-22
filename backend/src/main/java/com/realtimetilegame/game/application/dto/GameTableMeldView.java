package com.realtimetilegame.game.application.dto;

import java.util.List;

public record GameTableMeldView(
    String meldId,
    String meldType,
    int score,
    int positionOrder,
    int gridRow,
    int gridColumn,
    List<GameTableTileView> tiles
) {
    public GameTableMeldView {
        tiles = List.copyOf(tiles);
    }
}
