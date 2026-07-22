package com.realtimetilegame.game.domain.session;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRepository {
    List<GamePlayer> saveAllAndFlush(List<GamePlayer> players);
    List<GamePlayer> findByGameId(long gameId);
    Optional<GamePlayer> findByGameIdAndUserId(long gameId, long userId);
    boolean existsByGameIdAndUserId(long gameId, long userId);
    boolean existsActiveByGameIdAndUserId(long gameId, long userId);
    long countByGameId(long gameId);
}
