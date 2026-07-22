package com.realtimetilegame.game.domain.session;

import java.util.List;
import java.util.Optional;

public interface GameTileRepository {
    List<GameTile> saveAllAndFlush(List<GameTile> tiles);
    List<GameTile> findByGameId(long gameId);
    Optional<GameTile> findFirstPoolTileForUpdate(long gameId);
    int findNextRackPosition(long gamePlayerId);
    long countByGameId(long gameId);
    long countByGameIdAndLocation(long gameId, GameTileLocation location);
}
