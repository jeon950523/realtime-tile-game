package com.realtimetilegame.game.domain.state;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class MeldState {
    private final MeldId meldId;
    private final List<TileId> tileIds;

    public MeldState(MeldId meldId, Collection<TileId> tileIds) {
        this.meldId = Objects.requireNonNull(meldId, "meldId must not be null");
        Objects.requireNonNull(tileIds, "tileIds must not be null");
        List<TileId> copy = List.copyOf(tileIds);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("meld must not contain null tileId");
        }
        this.tileIds = copy;
    }

    public MeldId meldId() {
        return meldId;
    }

    public List<TileId> tileIds() {
        return tileIds;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof MeldState meldState
            && meldId.equals(meldState.meldId)
            && tileIds.equals(meldState.tileIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meldId, tileIds);
    }

    @Override
    public String toString() {
        return "MeldState[meldId=" + meldId + ", tileIds=" + tileIds + "]";
    }
}
