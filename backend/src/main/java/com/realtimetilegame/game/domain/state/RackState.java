package com.realtimetilegame.game.domain.state;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RackState {
    private final List<TileId> tileIds;

    public RackState(Collection<TileId> tileIds) {
        Objects.requireNonNull(tileIds, "tileIds must not be null");
        List<TileId> copy = List.copyOf(tileIds);
        requireNoNulls(copy);
        requireUnique(copy);
        this.tileIds = copy;
    }

    public static RackState empty() {
        return new RackState(List.of());
    }

    public List<TileId> tileIds() {
        return tileIds;
    }

    public boolean contains(TileId tileId) {
        return tileIds.contains(tileId);
    }

    public int size() {
        return tileIds.size();
    }

    public boolean isEmpty() {
        return tileIds.isEmpty();
    }

    public RackState without(Collection<TileId> removedTileIds) {
        Objects.requireNonNull(removedTileIds, "removedTileIds must not be null");
        Set<TileId> removed = Set.copyOf(removedTileIds);
        if (!tileIds.containsAll(removed)) {
            throw new IllegalArgumentException("cannot remove tile not owned by rack");
        }
        return new RackState(tileIds.stream().filter(id -> !removed.contains(id)).toList());
    }

    private static void requireNoNulls(List<TileId> tileIds) {
        if (tileIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rack must not contain null tileId");
        }
    }

    private static void requireUnique(List<TileId> tileIds) {
        if (new HashSet<>(tileIds).size() != tileIds.size()) {
            throw new IllegalArgumentException("rack must not contain duplicated tileId");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RackState rackState && tileIds.equals(rackState.tileIds);
    }

    @Override
    public int hashCode() {
        return tileIds.hashCode();
    }

    @Override
    public String toString() {
        return "RackState" + tileIds;
    }
}
