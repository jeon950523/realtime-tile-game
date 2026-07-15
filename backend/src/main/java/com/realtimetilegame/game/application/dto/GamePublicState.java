package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GamePublicState(
    long gameId,
    long roomId,
    String gameMode,
    String status,
    long currentTurnUserId,
    int currentTurnSeatOrder,
    int turnNumber,
    OffsetDateTime startedAt,
    int tilePoolCount,
    List<Object> tableMelds,
    List<GamePlayerPublicView> players
) {
    public GamePublicState {
        tableMelds = List.copyOf(tableMelds);
        players = List.copyOf(players);
    }
}
