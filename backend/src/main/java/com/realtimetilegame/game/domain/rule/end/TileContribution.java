package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Objects;

public record TileContribution(
    TileId tileId,
    ParticipantId contributedBy,
    int score,
    long committedAtVersion
) {
    public TileContribution {
        tileId = Objects.requireNonNull(tileId, "tileId must not be null");
        contributedBy = Objects.requireNonNull(contributedBy, "contributedBy must not be null");
        if (score < 1 || score > 13) {
            throw new IllegalArgumentException("contribution score must be between 1 and 13");
        }
        if (committedAtVersion < 0) {
            throw new IllegalArgumentException("committedAtVersion must not be negative");
        }
    }
}
