package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.tile.NumberTile;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record SpeedScoreContext(
    Map<ParticipantId, List<TileContribution>> contributions,
    Map<ParticipantId, RackState> remainingRacks,
    TileCatalog tileCatalog
) {
    public SpeedScoreContext {
        contributions = immutableContributionMap(contributions);
        remainingRacks = immutableRackMap(remainingRacks);
        tileCatalog = Objects.requireNonNull(tileCatalog, "tileCatalog must not be null");

        if (!remainingRacks.keySet().equals(contributions.keySet())) {
            throw new IllegalArgumentException("contribution and remaining-rack participants must match");
        }
        if (remainingRacks.size() < 2 || remainingRacks.size() > 4) {
            throw new IllegalArgumentException("participant count must be between 2 and 4");
        }

        Set<TileId> contributedTileIds = validateContributions(contributions, tileCatalog);
        validateRemainingRacks(remainingRacks, tileCatalog, contributedTileIds);
    }

    private static Map<ParticipantId, List<TileContribution>> immutableContributionMap(
        Map<ParticipantId, List<TileContribution>> source
    ) {
        Objects.requireNonNull(source, "contributions must not be null");
        Map<ParticipantId, List<TileContribution>> copy = new LinkedHashMap<>();
        source.forEach((participantId, participantContributions) -> copy.put(
            Objects.requireNonNull(participantId, "contribution participant must not be null"),
            List.copyOf(Objects.requireNonNull(
                participantContributions,
                "participant contributions must not be null"
            ))
        ));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ParticipantId, RackState> immutableRackMap(Map<ParticipantId, RackState> source) {
        Objects.requireNonNull(source, "remainingRacks must not be null");
        Map<ParticipantId, RackState> copy = new LinkedHashMap<>();
        source.forEach((participantId, rack) -> copy.put(
            Objects.requireNonNull(participantId, "remaining-rack participant must not be null"),
            Objects.requireNonNull(rack, "remaining rack must not be null")
        ));
        return Collections.unmodifiableMap(copy);
    }

    private static Set<TileId> validateContributions(
        Map<ParticipantId, List<TileContribution>> contributions,
        TileCatalog tileCatalog
    ) {
        Set<TileId> contributedTileIds = new LinkedHashSet<>();
        for (Map.Entry<ParticipantId, List<TileContribution>> entry : contributions.entrySet()) {
            for (TileContribution contribution : entry.getValue()) {
                if (!entry.getKey().equals(contribution.contributedBy())) {
                    throw new IllegalArgumentException("contribution owner must match the map participant");
                }
                if (!contributedTileIds.add(contribution.tileId())) {
                    throw new IllegalArgumentException("a tile contribution must be recorded exactly once");
                }
                if (!tileCatalog.contains(contribution.tileId())) {
                    throw new IllegalArgumentException("contribution tile must exist in the catalog");
                }
                Tile tile = tileCatalog.get(contribution.tileId());
                if (tile instanceof NumberTile numberTile && contribution.score() != numberTile.number()) {
                    throw new IllegalArgumentException("number-tile contribution score must match the tile number");
                }
            }
        }
        return contributedTileIds;
    }

    private static void validateRemainingRacks(
        Map<ParticipantId, RackState> remainingRacks,
        TileCatalog tileCatalog,
        Set<TileId> contributedTileIds
    ) {
        Set<TileId> remainingTileIds = new LinkedHashSet<>();
        for (RackState rack : remainingRacks.values()) {
            for (TileId tileId : rack.tileIds()) {
                if (!tileCatalog.contains(tileId)) {
                    throw new IllegalArgumentException("remaining rack tile must exist in the catalog");
                }
                if (!remainingTileIds.add(tileId)) {
                    throw new IllegalArgumentException("a remaining tile cannot exist in more than one rack");
                }
                if (contributedTileIds.contains(tileId)) {
                    throw new IllegalArgumentException("a contributed tile cannot remain in a participant rack");
                }
            }
        }
    }
}
