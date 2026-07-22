package com.realtimetilegame.game.event;

import java.time.OffsetDateTime;
import java.util.Map;

import com.realtimetilegame.game.application.dto.GamePrivateState;

public record GameTurnCommittedEvent(
    long gameId,
    String publicEventType,
    OffsetDateTime occurredAt,
    Object publicPayload,
    Map<Long, GamePrivateState> privateStates
) {
    public GameTurnCommittedEvent {
        privateStates = Map.copyOf(privateStates);
    }
}
