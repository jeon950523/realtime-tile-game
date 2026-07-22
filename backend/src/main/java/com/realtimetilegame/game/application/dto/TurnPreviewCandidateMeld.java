package com.realtimetilegame.game.application.dto;

import java.util.List;

public record TurnPreviewCandidateMeld(
    String meldId,
    List<String> tileIds,
    Integer gridRow,
    Integer gridColumn
) {
}
