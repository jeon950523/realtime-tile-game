package com.realtimetilegame.game.application.dto;

public record TurnPreviewCancelCommand(
    Long gameId,
    Long baseGameVersion,
    Long previewRevision
) {
}
