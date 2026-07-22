package com.realtimetilegame.game.application.dto;

import java.util.List;

public record CommitTableMeldCommand(
    String meldId,
    List<String> tileIds,
    Integer gridRow,
    Integer gridColumn
) {
    public CommitTableMeldCommand(String meldId, List<String> tileIds) {
        this(meldId, tileIds, 0, 0);
    }

    public CommitTableMeldCommand {
        tileIds = tileIds == null ? null : List.copyOf(tileIds);
    }
}
