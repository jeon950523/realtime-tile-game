package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import com.realtimetilegame.game.domain.state.InitialTileDistribution;
import com.realtimetilegame.game.domain.state.InitialTileDistributor;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameInitialDealTest {
    private final InitialTileDistributor distributor = new InitialTileDistributor();

    @ParameterizedTest
    @CsvSource({"2,78", "3,64", "4,50"})
    void distributesFourteenTilesPerRackAndKeepsTheExpectedPool(int playerCount, int expectedPool) {
        List<Tile> tiles = TileSetFactory.createStandardSet();
        List<ParticipantId> participants = participants(playerCount);

        InitialTileDistribution distribution = distributor.distribute(tiles, participants);

        assertThat(distribution.racks()).hasSize(playerCount);
        assertThat(distribution.racks().values())
            .allSatisfy(rack -> assertThat(rack.tileIds()).hasSize(InitialTileDistributor.INITIAL_RACK_SIZE));
        assertThat(distribution.tilePool().remainingTileIds()).hasSize(expectedPool);

        List<TileId> allIds = java.util.stream.Stream.concat(
                distribution.racks().values().stream().flatMap(rack -> rack.tileIds().stream()),
                distribution.tilePool().remainingTileIds().stream())
            .toList();
        assertThat(allIds).hasSize(TileSetFactory.STANDARD_TILE_COUNT);
        assertThat(new HashSet<>(allIds)).hasSize(TileSetFactory.STANDARD_TILE_COUNT);
        assertThat(allIds).containsExactlyInAnyOrderElementsOf(tiles.stream().map(Tile::id).toList());
    }

    @ParameterizedTest
    @CsvSource({"2", "3", "4"})
    void deterministicOrderDealsRoundRobinFromTheInjectedShuffleResult(int playerCount) {
        List<Tile> ordered = TileSetFactory.createStandardSet();
        List<ParticipantId> participants = participants(playerCount);

        InitialTileDistribution distribution = distributor.distribute(ordered, participants);

        for (int participantIndex = 0; participantIndex < playerCount; participantIndex++) {
            final int currentParticipantIndex = participantIndex;
            List<TileId> expected = IntStream.range(0, InitialTileDistributor.INITIAL_RACK_SIZE)
                .mapToObj(round -> ordered.get(round * playerCount + currentParticipantIndex).id())
                .toList();
            assertThat(distribution.racks().get(participants.get(currentParticipantIndex)).tileIds())
                .containsExactlyElementsOf(expected);
        }
    }

    private static List<ParticipantId> participants(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> new ParticipantId("user-" + index))
            .toList();
    }
}
