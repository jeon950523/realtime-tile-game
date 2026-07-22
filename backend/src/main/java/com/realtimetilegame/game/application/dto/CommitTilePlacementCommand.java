package com.realtimetilegame.game.application.dto;

public record CommitTilePlacementCommand(
    String tileId,
    Integer gridRow,
    Integer gridColumn
) {
}
