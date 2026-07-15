package com.realtimetilegame.game.domain.rule.model;

import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.List;
import java.util.Objects;

public record MeldCandidate(MeldId meldId, List<TileId> tileIds) {
    public MeldCandidate {
        meldId = Objects.requireNonNull(meldId, "meldId must not be null");
        Objects.requireNonNull(tileIds, "tileIds must not be null");
        tileIds = List.copyOf(tileIds);
        if (tileIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tileIds must not contain null");
        }
    }

    public static MeldCandidate from(MeldState meldState) {
        Objects.requireNonNull(meldState, "meldState must not be null");
        return new MeldCandidate(meldState.meldId(), meldState.tileIds());
    }
}
