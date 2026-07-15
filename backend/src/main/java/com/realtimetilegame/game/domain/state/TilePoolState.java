package com.realtimetilegame.game.domain.state;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class TilePoolState {
    private final List<TileId> remainingTileIds;

    public TilePoolState(Collection<TileId> remainingTileIds) {
        Objects.requireNonNull(remainingTileIds, "remainingTileIds must not be null");
        List<TileId> copy = List.copyOf(remainingTileIds);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tile pool must not contain null tileId");
        }
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("tile pool must not contain duplicated tileId");
        }
        this.remainingTileIds = copy;
    }

    public List<TileId> remainingTileIds() {
        return remainingTileIds;
    }

    public int size() {
        return remainingTileIds.size();
    }

    public boolean isEmpty() {
        return remainingTileIds.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TilePoolState that && remainingTileIds.equals(that.remainingTileIds);
    }

    @Override
    public int hashCode() {
        return remainingTileIds.hashCode();
    }

    @Override
    public String toString() {
        return "TilePoolState" + remainingTileIds;
    }
}
