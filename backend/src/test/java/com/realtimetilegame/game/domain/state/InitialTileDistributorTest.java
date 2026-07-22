package com.realtimetilegame.game.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class InitialTileDistributorTest {
    private final InitialTileDistributor distributor = new InitialTileDistributor();
    private final List<Tile> standardTiles = TileSetFactory.createStandardSet();

    @Test
    void game004DistributesFourteenTilesEachToTwoPlayersAndLeaves78() {
        assertDistribution(2, 78);
    }

    @Test
    void game005DistributesFourteenTilesEachToThreePlayersAndLeaves64() {
        assertDistribution(3, 64);
    }

    @Test
    void game006DistributesFourteenTilesEachToFourPlayersAndLeaves50() {
        assertDistribution(4, 50);
    }

    @Test
    void game007EveryTileExistsAtExactlyOneInitialLocation() {
        InitialTileDistribution result = distributor.distribute(standardTiles, participants(4));

        List<?> locatedTiles = java.util.stream.Stream.concat(
            result.racks().values().stream().flatMap(rack -> rack.tileIds().stream()),
            result.tilePool().remainingTileIds().stream()
        ).toList();

        assertThat(locatedTiles).hasSize(106).doesNotHaveDuplicates();
    }

    @Test
    void distributionCollectionsAreImmutable() {
        InitialTileDistribution result = distributor.distribute(standardTiles, participants(2));

        assertThatThrownBy(() -> result.racks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.racks().values().iterator().next().tileIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.tilePool().remainingTileIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertDistribution(int playerCount, int expectedPoolSize) {
        InitialTileDistribution result = distributor.distribute(standardTiles, participants(playerCount));

        assertThat(result.racks()).hasSize(playerCount);
        assertThat(result.racks().values()).allSatisfy(rack -> assertThat(rack.size()).isEqualTo(14));
        assertThat(result.tilePool().size()).isEqualTo(expectedPoolSize);
    }

    private static List<ParticipantId> participants(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> new ParticipantId("P" + index))
            .toList();
    }

    @Test
    void preservesParticipantInsertionOrder() {
        var participants = List.of(new ParticipantId("P3"), new ParticipantId("P1"), new ParticipantId("P2"));

        var distribution = distributor.distribute(TileSetFactory.createStandardSet(), participants);

        assertThat(distribution.racks().keySet()).containsExactlyElementsOf(participants);
    }

    @Test
    void rejectsNullParticipant() {
        List<ParticipantId> participants = new java.util.ArrayList<>();
        participants.add(new ParticipantId("P1"));
        participants.add(null);

        assertThatThrownBy(() -> distributor.distribute(standardTiles, participants))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("participants must not contain null");
    }

    @Test
    void initialDistributionRejectsNullParticipantKey() {
        Map<ParticipantId, RackState> racks = new LinkedHashMap<>();
        racks.put(new ParticipantId("P1"), RackState.empty());
        racks.put(null, RackState.empty());

        assertThatThrownBy(() -> new InitialTileDistribution(racks, new TilePoolState(List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("racks must not contain null participantId");
    }

    @Test
    void initialDistributionRejectsNullRack() {
        Map<ParticipantId, RackState> racks = new LinkedHashMap<>();
        racks.put(new ParticipantId("P1"), RackState.empty());
        racks.put(new ParticipantId("P2"), null);

        assertThatThrownBy(() -> new InitialTileDistribution(racks, new TilePoolState(List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("racks must not contain null rack");
    }
}
