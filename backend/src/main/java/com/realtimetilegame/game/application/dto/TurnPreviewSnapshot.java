package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TurnPreviewSnapshot(
    long gameId,
    long turnPlayerId,
    long baseGameVersion,
    long previewRevision,
    List<TurnPreviewTilePlacement> tilePlacements,
    @JsonIgnore List<TurnPreviewCandidateMeld> candidateMelds,
    OffsetDateTime updatedAt
) {
    public TurnPreviewSnapshot(long gameId, long turnPlayerId, long baseGameVersion, long previewRevision,
                               List<TurnPreviewCandidateMeld> candidateMelds, OffsetDateTime updatedAt) {
        this(gameId, turnPlayerId, baseGameVersion, previewRevision, null, candidateMelds, updatedAt);
    }

    public TurnPreviewSnapshot {
        tilePlacements = tilePlacements == null ? null : List.copyOf(tilePlacements);
        candidateMelds = candidateMelds == null ? null : List.copyOf(candidateMelds);
    }
}
