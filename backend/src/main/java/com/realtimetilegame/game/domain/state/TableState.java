package com.realtimetilegame.game.domain.state;

import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class TableState {
    private final List<MeldState> melds;

    public TableState(Collection<MeldState> melds) {
        Objects.requireNonNull(melds, "melds must not be null");
        List<MeldState> copy = List.copyOf(melds);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("table must not contain null meld");
        }
        this.melds = copy;
    }

    public static TableState empty() {
        return new TableState(List.of());
    }

    public List<MeldState> melds() {
        return melds;
    }

    public List<TileId> allTileIds() {
        return melds.stream().flatMap(meld -> meld.tileIds().stream()).toList();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TableState tableState && melds.equals(tableState.melds);
    }

    @Override
    public int hashCode() {
        return melds.hashCode();
    }

    @Override
    public String toString() {
        return "TableState" + melds;
    }
}
