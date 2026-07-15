package com.realtimetilegame.game.domain.state;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TileLocationState {
    private final TilePoolState tilePool;
    private final Map<ParticipantId, RackState> racks;
    private final TableState table;

    public TileLocationState(
        TilePoolState tilePool,
        Map<ParticipantId, RackState> racks,
        TableState table
    ) {
        this.tilePool = Objects.requireNonNull(tilePool, "tilePool must not be null");
        Objects.requireNonNull(racks, "racks must not be null");
        Map<ParticipantId, RackState> copy = new LinkedHashMap<>();
        racks.forEach((participantId, rack) -> copy.put(
            Objects.requireNonNull(participantId, "participantId must not be null"),
            Objects.requireNonNull(rack, "rack must not be null")
        ));
        this.racks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(copy));
        this.table = Objects.requireNonNull(table, "table must not be null");
    }

    public TilePoolState tilePool() {
        return tilePool;
    }

    public Map<ParticipantId, RackState> racks() {
        return racks;
    }

    public RackState rackOf(ParticipantId participantId) {
        RackState rack = racks.get(participantId);
        if (rack == null) {
            throw new IllegalArgumentException("participant rack not found: " + participantId);
        }
        return rack;
    }

    public TableState table() {
        return table;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof TileLocationState that
            && tilePool.equals(that.tilePool)
            && racks.equals(that.racks)
            && table.equals(that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tilePool, racks, table);
    }

    @Override
    public String toString() {
        return "TileLocationState[tilePool=" + tilePool + ", racks=" + racks + ", table=" + table + "]";
    }
}
