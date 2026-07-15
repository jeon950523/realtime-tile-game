package com.realtimetilegame.game.domain.state;

import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InitialTileDistributor {
    public static final int INITIAL_RACK_SIZE = 14;

    public InitialTileDistribution distribute(List<? extends Tile> orderedTiles, List<ParticipantId> participants) {
        Objects.requireNonNull(orderedTiles, "orderedTiles must not be null");
        Objects.requireNonNull(participants, "participants must not be null");
        if (orderedTiles.size() != TileSetFactory.STANDARD_TILE_COUNT) {
            throw new IllegalArgumentException("standard distribution requires exactly 106 tiles");
        }
        if (participants.size() < 2 || participants.size() > 4) {
            throw new IllegalArgumentException("participant count must be between 2 and 4");
        }
        if (participants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("participants must not contain null");
        }
        if (new HashSet<>(participants).size() != participants.size()) {
            throw new IllegalArgumentException("participants must be unique");
        }
        if (orderedTiles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("orderedTiles must not contain null");
        }
        List<TileId> orderedIds = orderedTiles.stream().map(Tile::id).toList();
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new IllegalArgumentException("orderedTiles must have unique tileIds");
        }

        Map<ParticipantId, List<TileId>> mutableRacks = new LinkedHashMap<>();
        participants.forEach(participant -> mutableRacks.put(participant, new ArrayList<>(INITIAL_RACK_SIZE)));

        int dealtCount = participants.size() * INITIAL_RACK_SIZE;
        for (int index = 0; index < dealtCount; index++) {
            ParticipantId participant = participants.get(index % participants.size());
            mutableRacks.get(participant).add(orderedIds.get(index));
        }

        Map<ParticipantId, RackState> racks = new LinkedHashMap<>();
        mutableRacks.forEach((participant, tileIds) -> racks.put(participant, new RackState(tileIds)));
        TilePoolState tilePool = new TilePoolState(orderedIds.subList(dealtCount, orderedIds.size()));
        return new InitialTileDistribution(racks, tilePool);
    }
}
