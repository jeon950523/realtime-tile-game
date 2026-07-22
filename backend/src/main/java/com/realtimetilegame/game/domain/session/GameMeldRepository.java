package com.realtimetilegame.game.domain.session;

import java.util.List;

public interface GameMeldRepository {
    List<GameMeld> saveAllAndFlush(List<GameMeld> melds);
    void deleteAllAndFlush(List<GameMeld> melds);
    List<GameMeld> findByGameId(long gameId);
    long countByGameId(long gameId);
    boolean existsByGameIdAndMeldId(long gameId, String meldId);
    int findNextPosition(long gameId);
}
