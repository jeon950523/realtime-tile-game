package com.realtimetilegame.game.domain.session;

import java.util.List;

public interface GameTileRepository {
    List<GameTile> saveAllAndFlush(List<GameTile> tiles);
    List<GameTile> findByGameId(long gameId);
    long countByGameId(long gameId);
    long countByGameIdAndLocation(long gameId, GameTileLocation location);
}
