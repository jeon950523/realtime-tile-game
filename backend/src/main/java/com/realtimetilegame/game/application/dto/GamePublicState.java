package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GamePublicState(
    long gameId,
    long roomId,
    String gameMode,
    String status,
    long gameVersion,
    long currentTurnUserId,
    int currentTurnSeatOrder,
    int turnNumber,
    String currentTurnId,
    OffsetDateTime currentTurnStartedAt,
    OffsetDateTime turnDeadlineAt,
    int consecutivePassCount,
    OffsetDateTime startedAt,
    int tilePoolCount,
    List<GameTableMeldView> tableMelds,
    List<GamePlayerPublicView> players
) {
    public GamePublicState {
        tableMelds = List.copyOf(tableMelds);
        players = List.copyOf(players);
    }
}
