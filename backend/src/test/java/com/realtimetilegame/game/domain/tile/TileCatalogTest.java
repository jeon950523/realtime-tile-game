package com.realtimetilegame.game.domain.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class TileCatalogTest {
    @Test
    void rejectsDuplicatedTileIdAtConstruction() {
        TileId id = new TileId("RED-01-A");

        assertThatThrownBy(() -> new TileCatalog(List.of(
            new NumberTile(id, TileColor.RED, 1),
            new NumberTile(id, TileColor.BLUE, 1)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicated tileId");
    }

    @Test
    void returnsRegisteredTileAndRejectsUnknownId() {
        NumberTile tile = new NumberTile(new TileId("RED-01-A"), TileColor.RED, 1);
        TileCatalog catalog = new TileCatalog(List.of(tile));

        assertThat(catalog.get(tile.id())).isEqualTo(tile);
        assertThatThrownBy(() -> catalog.get(new TileId("UNKNOWN")))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void preservesRegistrationOrderDeterministically() {
        var tiles = TileSetFactory.createStandardSet();
        var catalog = new TileCatalog(tiles);

        assertThat(catalog.tiles()).containsExactlyElementsOf(tiles);
    }
}
