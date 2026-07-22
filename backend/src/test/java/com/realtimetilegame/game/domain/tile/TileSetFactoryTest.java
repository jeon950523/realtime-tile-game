package com.realtimetilegame.game.domain.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TileSetFactoryTest {
    @Test
    void game001CreatesExactly106UniqueTileIds() {
        List<Tile> tiles = TileSetFactory.createStandardSet();

        assertThat(tiles).hasSize(106);
        assertThat(tiles.stream().map(Tile::id)).doesNotHaveDuplicates();
    }

    @Test
    void game002CreatesTwoPhysicalTilesForEveryColorAndNumber() {
        List<NumberTile> numberTiles = TileSetFactory.createStandardSet().stream()
            .filter(NumberTile.class::isInstance)
            .map(NumberTile.class::cast)
            .toList();

        Map<String, Long> counts = numberTiles.stream().collect(Collectors.groupingBy(
            tile -> tile.color() + "-" + tile.number(),
            Collectors.counting()
        ));

        assertThat(numberTiles).hasSize(104);
        assertThat(counts).hasSize(52).allSatisfy((key, count) -> assertThat(count).isEqualTo(2));
    }

    @Test
    void game003CreatesTwoJokers() {
        assertThat(TileSetFactory.createStandardSet())
            .filteredOn(JokerTile.class::isInstance)
            .extracting(Tile::id)
            .containsExactly(new TileId("JOKER-A"), new TileId("JOKER-B"));
    }

    @Test
    void standardSetCannotBeModifiedExternally() {
        List<Tile> tiles = TileSetFactory.createStandardSet();

        assertThatThrownBy(() -> tiles.add(new JokerTile(new TileId("OTHER-JOKER"))))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
