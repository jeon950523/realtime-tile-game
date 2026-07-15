package com.realtimetilegame.game.event;

import java.time.OffsetDateTime;
import java.util.Map;

import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GameStartedPayload;

public record GameStartedCommittedEvent(
    long gameId,
    long roomId,
    OffsetDateTime occurredAt,
    GameStartedPayload launchPayload,
    Map<Long, GamePrivateState> privateStates
) {
    public GameStartedCommittedEvent {
        privateStates = Map.copyOf(privateStates);
    }
}
