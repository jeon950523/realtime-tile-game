package com.realtimetilegame.game.support;

import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.state.TilePoolState;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleTestFixtures {
    public static final ParticipantId P1 = new ParticipantId("P1");
    public static final ParticipantId P2 = new ParticipantId("P2");
    public static final TileId JOKER_A = new TileId("JOKER-A");
    public static final TileId JOKER_B = new TileId("JOKER-B");
    public static final List<Tile> STANDARD_TILES = TileSetFactory.createStandardSet();
    public static final TileCatalog CATALOG = new TileCatalog(STANDARD_TILES);
    public static final Set<TileId> CANONICAL_IDS = CATALOG.tileIds();

    private RuleTestFixtures() {
    }

    public static TileId id(TileColor color, int number) {
        return id(color, number, "A");
    }

    public static TileId id(TileColor color, int number, String copy) {
        return new TileId("%s-%02d-%s".formatted(color.name(), number, copy));
    }

    public static MeldCandidate candidate(String id, TileId... tileIds) {
        return new MeldCandidate(new MeldId(id), List.of(tileIds));
    }

    public static MeldState meld(String id, TileId... tileIds) {
        return new MeldState(new MeldId(id), List.of(tileIds));
    }

    public static TableState table(MeldState... melds) {
        return new TableState(List.of(melds));
    }

    public static TileLocationState state(TableState table, TileId... p1RackIds) {
        return state(table, Map.of(P1, new RackState(List.of(p1RackIds))));
    }

    public static TileLocationState state(TableState table, Map<ParticipantId, RackState> racks) {
        Map<ParticipantId, RackState> normalizedRacks = withMinimumParticipants(racks);
        Set<TileId> located = new LinkedHashSet<>();
        normalizedRacks.values().forEach(rack -> located.addAll(rack.tileIds()));
        located.addAll(table.allTileIds());
        List<TileId> pool = STANDARD_TILES.stream()
            .map(Tile::id)
            .filter(tileId -> !located.contains(tileId))
            .toList();
        return new TileLocationState(new TilePoolState(pool), normalizedRacks, table);
    }

    public static TileLocationState stateWithPool(
        TableState table,
        Map<ParticipantId, RackState> racks,
        Collection<TileId> poolTileIds
    ) {
        return new TileLocationState(new TilePoolState(poolTileIds), withMinimumParticipants(racks), table);
    }

    private static Map<ParticipantId, RackState> withMinimumParticipants(Map<ParticipantId, RackState> racks) {
        Map<ParticipantId, RackState> normalized = new LinkedHashMap<>(racks);
        normalized.putIfAbsent(P2, RackState.empty());
        return java.util.Collections.unmodifiableMap(normalized);
    }

    public static List<TileId> poolWithout(TileLocationState state, TileId... removed) {
        Set<TileId> removedSet = Set.copyOf(Arrays.asList(removed));
        return state.tilePool().remainingTileIds().stream().filter(id -> !removedSet.contains(id)).toList();
    }

    public static Map<ParticipantId, RackState> replaceRack(
        Map<ParticipantId, RackState> racks,
        ParticipantId participantId,
        RackState replacement
    ) {
        Map<ParticipantId, RackState> result = new LinkedHashMap<>(racks);
        result.put(participantId, replacement);
        return Map.copyOf(result);
    }

    public static List<TileId> ids(TileColor color, int startInclusive, int endInclusive) {
        List<TileId> result = new ArrayList<>();
        for (int number = startInclusive; number <= endInclusive; number++) {
            result.add(id(color, number));
        }
        return List.copyOf(result);
    }
}
