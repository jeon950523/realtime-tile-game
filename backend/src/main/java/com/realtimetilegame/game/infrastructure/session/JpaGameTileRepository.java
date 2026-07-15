package com.realtimetilegame.game.infrastructure.session;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;

@Repository
public class JpaGameTileRepository implements GameTileRepository {
    private final SpringDataGameTileJpaRepository repository;

    public JpaGameTileRepository(SpringDataGameTileJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<GameTile> saveAllAndFlush(List<GameTile> tiles) {
        return repository.saveAllAndFlush(tiles);
    }
    @Override public List<GameTile> findByGameId(long gameId) { return repository.findByGameId(gameId); }
    @Override public long countByGameId(long gameId) { return repository.countByGameId(gameId); }
    @Override public long countByGameIdAndLocation(long gameId, GameTileLocation location) {
        return repository.countByGameIdAndLocation(gameId, location);
    }
}
