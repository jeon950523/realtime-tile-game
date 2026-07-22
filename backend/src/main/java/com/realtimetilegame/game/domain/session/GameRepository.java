package com.realtimetilegame.game.domain.session;

import java.util.Optional;

public interface GameRepository {
    Game save(Game game);
    Game saveAndFlush(Game game);
    Optional<Game> findById(long gameId);
    Optional<Game> findByIdForUpdate(long gameId);
    Optional<Game> findByRoomId(long roomId);
    Optional<Game> findActiveByUserId(long userId);
    boolean existsByRoomId(long roomId);
    long countByRoomId(long roomId);
}
