package com.realtimetilegame.game.application.dto;

public record GameRackTileView(
    String tileId,
    String tileType,
    String color,
    Integer number,
    boolean joker,
    int positionOrder
) {
}
