package com.realtimetilegame.game.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CommitTurnCommand(
    String actionId,
    Long gameVersion,
    List<CommitTilePlacementCommand> tilePlacements,
    @JsonIgnore List<CommitTableMeldCommand> tableMelds
) {
    public CommitTurnCommand(String actionId, Long gameVersion, List<CommitTableMeldCommand> tableMelds) {
        this(actionId, gameVersion, null, tableMelds);
    }

    public CommitTurnCommand {
        tilePlacements = tilePlacements == null ? null : List.copyOf(tilePlacements);
        tableMelds = tableMelds == null ? null : List.copyOf(tableMelds);
    }
}
