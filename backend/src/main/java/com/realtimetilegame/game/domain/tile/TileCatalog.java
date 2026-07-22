package com.realtimetilegame.game.domain.tile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public final class TileCatalog {
    private final Map<TileId, Tile> tilesById;

    public TileCatalog(Collection<? extends Tile> tiles) {
        Objects.requireNonNull(tiles, "tiles must not be null");
        Map<TileId, Tile> indexed = new LinkedHashMap<>();
        for (Tile tile : tiles) {
            Objects.requireNonNull(tile, "tile must not be null");
            if (indexed.putIfAbsent(tile.id(), tile) != null) {
                throw new IllegalArgumentException("duplicated tileId: " + tile.id());
            }
        }
        this.tilesById = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public Tile get(TileId tileId) {
        Objects.requireNonNull(tileId, "tileId must not be null");
        Tile tile = tilesById.get(tileId);
        if (tile == null) {
            throw new NoSuchElementException("tile not found: " + tileId);
        }
        return tile;
    }

    public boolean contains(TileId tileId) {
        return tileId != null && tilesById.containsKey(tileId);
    }

    public Set<TileId> tileIds() {
        return tilesById.keySet();
    }

    public List<Tile> tiles() {
        return List.copyOf(tilesById.values());
    }

    public int size() {
        return tilesById.size();
    }
}
