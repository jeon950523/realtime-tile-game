package com.realtimetilegame.game.application.dto;

public record TurnPreviewTilePlacement(
    String tileId,
    Integer gridRow,
    Integer gridColumn,
    String source
) {
}
