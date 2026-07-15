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
import com.realtimetilegame.game.domain.session.Game;
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
        validateWholeState(game, players, tiles);
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

        return new GamePublicState(
            game.id(),
            game.room().id(),
            game.gameMode().name(),
            game.status().name(),
            game.currentTurnUser().id(),
            game.currentTurnSeatOrder(),
            game.turnNumber(),
            OffsetDateTime.of(game.startedAt(), ZoneOffset.UTC),
            poolCount,
            List.of(),
            publicPlayers
        );
    }

    public GamePrivateState privateState(Game game, List<GamePlayer> players, List<GameTile> tiles, long userId) {
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
            publicState(game, players, tiles),
            mine.id(),
            mine.user().id(),
            mine.seatOrder(),
            rack
        );
    }

    public Map<Long, GamePrivateState> privateStates(Game game, List<GamePlayer> players, List<GameTile> tiles) {
        Map<Long, GamePrivateState> states = new java.util.LinkedHashMap<>();
        players.stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder))
            .forEach(player -> states.put(player.user().id(), privateState(game, players, tiles, player.user().id())));
        return Map.copyOf(states);
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

    private static void validateWholeState(Game game, List<GamePlayer> players, List<GameTile> tiles) {
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
        Map<Long, Long> rackCounts = new HashMap<>();
        int poolCount = 0;
        for (GameTile tile : tiles) {
            if (tile.location() == GameTileLocation.RACK) {
                if (tile.owner() == null || !playerIds.contains(tile.owner().id())) {
                    throw new IllegalStateException("rack tile owner must belong to the game");
                }
                rackCounts.merge(tile.owner().id(), 1L, Long::sum);
            } else if (tile.location() == GameTileLocation.POOL) {
                if (tile.owner() != null) throw new IllegalStateException("pool tile must not have an owner");
                poolCount++;
            } else {
                throw new IllegalStateException("Phase 4 state must not contain table tiles");
            }
        }
        if (players.stream().anyMatch(player ->
            rackCounts.getOrDefault(player.id(), 0L) != com.realtimetilegame.game.domain.state.InitialTileDistributor.INITIAL_RACK_SIZE)) {
            throw new IllegalStateException("every player must have exactly 14 rack tiles");
        }
        int expectedPool = TileSetFactory.STANDARD_TILE_COUNT
            - players.size() * com.realtimetilegame.game.domain.state.InitialTileDistributor.INITIAL_RACK_SIZE;
        if (poolCount != expectedPool) {
            throw new IllegalStateException("tile pool count does not match the player count");
        }
        boolean currentTurnPlayerExists = players.stream().anyMatch(player ->
            player.user().id().equals(game.currentTurnUser().id())
                && player.seatOrder() == game.currentTurnSeatOrder());
        if (!currentTurnPlayerExists) {
            throw new IllegalStateException("current turn player must belong to the game");
        }
    }
}
