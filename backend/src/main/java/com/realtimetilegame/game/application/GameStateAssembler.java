package com.realtimetilegame.game.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.realtimetilegame.game.application.dto.GamePlayerPublicView;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GamePublicState;
import com.realtimetilegame.game.application.dto.GameRackTileView;
import com.realtimetilegame.game.application.dto.GameTableMeldView;
import com.realtimetilegame.game.application.dto.GameTableTileView;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeld;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.tile.JokerTile;
import com.realtimetilegame.game.domain.tile.NumberTile;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

@Component
public class GameStateAssembler {
    private final TileCatalog tileCatalog = new TileCatalog(TileSetFactory.createStandardSet());

    public GamePublicState publicState(Game game, List<GamePlayer> players, List<GameTile> tiles) {
        return publicState(game, players, tiles, List.of());
    }

    public GamePublicState publicState(Game game, List<GamePlayer> players, List<GameTile> tiles,
                                       List<GameMeld> melds) {
        Map<String, PersistedTableGridLayoutResolver.Coordinate> tableCoordinates =
            validateWholeState(game, players, tiles, melds);
        Map<Long, Integer> rackCounts = new HashMap<>();
        int poolCount = 0;
        for (GameTile tile : tiles) {
            if (tile.location() == GameTileLocation.RACK) {
                rackCounts.merge(tile.owner().id(), 1, Integer::sum);
            } else if (tile.location() == GameTileLocation.POOL) {
                poolCount++;
            }
        }

        List<GamePlayerPublicView> publicPlayers = players.stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder))
            .map(player -> new GamePlayerPublicView(
                player.user().id(),
                player.user().nickname(),
                player.user().avatarType().name(),
                player.seatOrder(),
                rackCounts.getOrDefault(player.id(), 0),
                player.initialMeldCompleted(),
                player.user().id().equals(game.currentTurnUser().id())
            ))
            .toList();

        List<GameTableMeldView> tableMelds = melds.stream()
            .sorted(java.util.Comparator.comparingInt(GameMeld::positionOrder))
            .map(meld -> tableMeld(meld, tiles, tableCoordinates.get(meld.meldId())))
            .toList();

        return new GamePublicState(
            game.id(),
            game.room().id(),
            game.gameMode().name(),
            game.status().name(),
            game.version(),
            game.currentTurnUser().id(),
            game.currentTurnSeatOrder(),
            game.turnNumber(),
            game.currentTurnId(),
            OffsetDateTime.of(game.currentTurnStartedAt(), ZoneOffset.UTC),
            OffsetDateTime.of(game.currentTurnDeadlineAt(), ZoneOffset.UTC),
            game.consecutivePassCount(),
            OffsetDateTime.of(game.startedAt(), ZoneOffset.UTC),
            poolCount,
            tableMelds,
            publicPlayers
        );
    }

    public GamePrivateState privateState(Game game, List<GamePlayer> players, List<GameTile> tiles, long userId) {
        return privateState(game, players, tiles, List.of(), userId);
    }

    public GamePrivateState privateState(Game game, List<GamePlayer> players, List<GameTile> tiles,
                                         List<GameMeld> melds, long userId) {
        GamePlayer mine = players.stream()
            .filter(player -> player.user().id() == userId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("game membership is required"));

        List<GameRackTileView> rack = tiles.stream()
            .filter(tile -> tile.location() == GameTileLocation.RACK)
            .filter(tile -> tile.owner().id().equals(mine.id()))
            .sorted(java.util.Comparator.comparingInt(GameTile::positionOrder))
            .map(this::rackTile)
            .toList();

        return new GamePrivateState(
            publicState(game, players, tiles, melds),
            mine.id(),
            mine.user().id(),
            mine.seatOrder(),
            rack
        );
    }

    public Map<Long, GamePrivateState> privateStates(Game game, List<GamePlayer> players, List<GameTile> tiles) {
        return privateStates(game, players, tiles, List.of());
    }

    public Map<Long, GamePrivateState> privateStates(Game game, List<GamePlayer> players, List<GameTile> tiles,
                                                     List<GameMeld> melds) {
        Map<Long, GamePrivateState> states = new java.util.LinkedHashMap<>();
        players.stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder))
            .forEach(player -> states.put(
                player.user().id(),
                privateState(game, players, tiles, melds, player.user().id())
            ));
        return Map.copyOf(states);
    }

    private GameTableMeldView tableMeld(
        GameMeld meld,
        List<GameTile> tiles,
        PersistedTableGridLayoutResolver.Coordinate coordinate
    ) {
        List<GameTableTileView> meldTiles = tiles.stream()
            .filter(tile -> tile.location() == GameTileLocation.TABLE)
            .filter(tile -> tile.meld().id().equals(meld.id()))
            .sorted(java.util.Comparator.comparingInt(GameTile::positionOrder))
            .map(this::tableTile)
            .toList();
        return new GameTableMeldView(
            meld.meldId(),
            meld.meldType().name(),
            meld.score(),
            meld.positionOrder(),
            coordinate.gridRow(),
            coordinate.gridColumn(),
            meld.lastModifiedBy().user().id(),
            meld.lastModifiedBy().seatOrder(),
            meldTiles
        );
    }

    private GameTableTileView tableTile(GameTile gameTile) {
        Tile tile = tileCatalog.get(gameTile.tileId());
        if (tile instanceof NumberTile numberTile) {
            return new GameTableTileView(
                numberTile.id().value(), "NUMBER", numberTile.color().name(), numberTile.number(), false,
                gameTile.positionOrder()
            );
        }
        if (tile instanceof JokerTile jokerTile) {
            return new GameTableTileView(
                jokerTile.id().value(), "JOKER", null, null, true, gameTile.positionOrder()
            );
        }
        throw new IllegalStateException("unsupported tile type");
    }

    private GameRackTileView rackTile(GameTile gameTile) {
        Tile tile = tileCatalog.get(gameTile.tileId());
        if (tile instanceof NumberTile numberTile) {
            return new GameRackTileView(
                numberTile.id().value(), "NUMBER", numberTile.color().name(), numberTile.number(), false,
                gameTile.positionOrder()
            );
        }
        if (tile instanceof JokerTile jokerTile) {
            return new GameRackTileView(
                jokerTile.id().value(), "JOKER", null, null, true, gameTile.positionOrder()
            );
        }
        throw new IllegalStateException("unsupported tile type");
    }

    private static Map<String, PersistedTableGridLayoutResolver.Coordinate> validateWholeState(
        Game game,
        List<GamePlayer> players,
        List<GameTile> tiles,
        List<GameMeld> melds
    ) {
        if (game.id() == null || game.room().id() == null) {
            throw new IllegalStateException("persisted game is required");
        }
        if (players.size() < 2 || players.size() > 4) {
            throw new IllegalStateException("game player count must be between 2 and 4");
        }
        if (tiles.size() != TileSetFactory.STANDARD_TILE_COUNT) {
            throw new IllegalStateException("game must contain exactly 106 tiles");
        }
        if (new HashSet<>(tiles.stream().map(tile -> tile.tileId().value()).toList()).size() != tiles.size()) {
            throw new IllegalStateException("game tiles must be unique");
        }
        List<Integer> seats = new ArrayList<>(players.stream().map(GamePlayer::seatOrder).toList());
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new IllegalStateException("game player seats must be unique");
        }
        Set<Long> playerIds = players.stream().map(GamePlayer::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> meldIds = new HashSet<>();
        Set<String> publicMeldIds = new HashSet<>();
        Set<Integer> meldPositions = new HashSet<>();
        for (GameMeld meld : melds) {
            if (meld.id() == null || !meld.game().id().equals(game.id())) {
                throw new IllegalStateException("table meld must belong to the game");
            }
            if (!playerIds.contains(meld.createdBy().id())) {
                throw new IllegalStateException("table meld creator must belong to the game");
            }
            if (meld.lastModifiedBy() == null || !playerIds.contains(meld.lastModifiedBy().id())) {
                throw new IllegalStateException("table meld last modifier must belong to the game");
            }
            if (!meldIds.add(meld.id()) || !publicMeldIds.add(meld.meldId())) {
                throw new IllegalStateException("table meld identifiers must be unique");
            }
            if (!meldPositions.add(meld.positionOrder())) {
                throw new IllegalStateException("table meld positions must be unique");
            }
        }
        Map<Long, Long> rackCounts = new HashMap<>();
        int poolCount = 0;
        int tableCount = 0;
        Map<Long, Integer> tableTileCounts = new HashMap<>();
        for (GameTile tile : tiles) {
            if (tile.location() == GameTileLocation.RACK) {
                if (tile.owner() == null || tile.meld() != null || !playerIds.contains(tile.owner().id())) {
                    throw new IllegalStateException("rack tile owner must belong to the game");
                }
                rackCounts.merge(tile.owner().id(), 1L, Long::sum);
            } else if (tile.location() == GameTileLocation.POOL) {
                if (tile.owner() != null || tile.meld() != null) {
                    throw new IllegalStateException("pool tile must not have owner or meld");
                }
                poolCount++;
            } else {
                if (tile.owner() != null || tile.meld() == null || !meldIds.contains(tile.meld().id())) {
                    throw new IllegalStateException("table tile must reference a meld from the same game");
                }
                tableCount++;
                tableTileCounts.merge(tile.meld().id(), 1, Integer::sum);
            }
        }
        if (melds.stream().anyMatch(meld -> tableTileCounts.getOrDefault(meld.id(), 0) == 0)) {
            throw new IllegalStateException("persisted table meld must contain tiles");
        }
        List<PersistedTableGridLayoutResolver.StoredPlacement> placements = melds.stream()
            .sorted(java.util.Comparator.comparingInt(GameMeld::positionOrder))
            .map(meld -> new PersistedTableGridLayoutResolver.StoredPlacement(
                meld.meldId(), tableTileCounts.getOrDefault(meld.id(), 0), meld.gridRow(), meld.gridColumn()
            ))
            .toList();
        Map<String, PersistedTableGridLayoutResolver.Coordinate> tableCoordinates =
            new PersistedTableGridLayoutResolver().resolve(placements);
        long rackTileCount = rackCounts.values().stream().mapToLong(Long::longValue).sum();
        if (rackTileCount + poolCount + tableCount != TileSetFactory.STANDARD_TILE_COUNT) {
            throw new IllegalStateException("pool, rack, and table tiles must account for all 106 tiles");
        }
        validateUniquePositions(tiles);
        boolean currentTurnPlayerExists = players.stream().anyMatch(player ->
            player.user().id().equals(game.currentTurnUser().id())
                && player.seatOrder() == game.currentTurnSeatOrder());
        if (!currentTurnPlayerExists) {
            throw new IllegalStateException("current turn player must belong to the game");
        }
        return tableCoordinates;
    }

    private static void validateUniquePositions(List<GameTile> tiles) {
        Set<Integer> poolPositions = new HashSet<>();
        Map<Long, Set<Integer>> rackPositions = new HashMap<>();
        Map<Long, Set<Integer>> tablePositions = new HashMap<>();
        for (GameTile tile : tiles) {
            if (tile.location() == GameTileLocation.POOL) {
                if (!poolPositions.add(tile.positionOrder())) {
                    throw new IllegalStateException("pool positions must be unique");
                }
                continue;
            }
            if (tile.location() == GameTileLocation.TABLE) {
                Set<Integer> positions = tablePositions.computeIfAbsent(tile.meld().id(), ignored -> new HashSet<>());
                if (!positions.add(tile.positionOrder())) {
                    throw new IllegalStateException("table tile positions must be unique per meld");
                }
                continue;
            }
            Set<Integer> positions = rackPositions.computeIfAbsent(tile.owner().id(), ignored -> new HashSet<>());
            if (!positions.add(tile.positionOrder())) {
                throw new IllegalStateException("rack positions must be unique per player");
            }
        }
    }
}
