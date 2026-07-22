package com.realtimetilegame.game.application.dto;

public record TurnPreviewClearedPayload(
    long gameId,
    long turnPlayerId,
    long baseGameVersion,
    long previewRevision,
    String reason
) {
}
