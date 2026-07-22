package com.realtimetilegame.game.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TurnPreviewCommand(
    Long gameId,
    Long baseGameVersion,
    Long previewRevision,
    List<TurnPreviewTilePlacement> tilePlacements,
    @JsonIgnore List<TurnPreviewCandidateMeld> candidateMelds
) {
    public TurnPreviewCommand(Long gameId, Long baseGameVersion, Long previewRevision,
                              List<TurnPreviewCandidateMeld> candidateMelds) {
        this(gameId, baseGameVersion, previewRevision, null, candidateMelds);
    }
}
