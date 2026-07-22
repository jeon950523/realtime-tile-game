package com.realtimetilegame.game.domain.state;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record InitialTileDistribution(
    Map<ParticipantId, RackState> racks,
    TilePoolState tilePool
) {
    public InitialTileDistribution {
        Objects.requireNonNull(racks, "racks must not be null");
        Map<ParticipantId, RackState> copy = new LinkedHashMap<>();
        for (Map.Entry<ParticipantId, RackState> entry : racks.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("racks must not contain null participantId");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("racks must not contain null rack");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        racks = java.util.Collections.unmodifiableMap(copy);
        tilePool = Objects.requireNonNull(tilePool, "tilePool must not be null");
    }
}
