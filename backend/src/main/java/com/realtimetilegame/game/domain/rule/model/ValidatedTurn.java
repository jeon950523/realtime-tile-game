package com.realtimetilegame.game.domain.rule.model;

import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ValidatedTurn(
    TableState committedTableCandidate,
    RackState committedRackCandidate,
    List<ValidatedMeld> validatedMelds,
    Set<TileId> rackToTableTiles,
    int initialMeldScore,
    boolean initialMeldCompletedAfterValidation,
    boolean rackEmpty
) {
    public ValidatedTurn {
        committedTableCandidate = Objects.requireNonNull(committedTableCandidate, "committedTableCandidate must not be null");
        committedRackCandidate = Objects.requireNonNull(committedRackCandidate, "committedRackCandidate must not be null");
        validatedMelds = List.copyOf(Objects.requireNonNull(validatedMelds, "validatedMelds must not be null"));
        Objects.requireNonNull(rackToTableTiles, "rackToTableTiles must not be null");
        rackToTableTiles = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(rackToTableTiles));
        if (initialMeldScore < 0) {
            throw new IllegalArgumentException("initialMeldScore must not be negative");
        }
    }
}
