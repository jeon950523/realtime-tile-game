package com.realtimetilegame.game.infrastructure.session;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.game.domain.session.GameMeld;
import com.realtimetilegame.game.domain.session.GameMeldRepository;

@Repository
public class JpaGameMeldRepository implements GameMeldRepository {
    private final SpringDataGameMeldJpaRepository repository;

    public JpaGameMeldRepository(SpringDataGameMeldJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<GameMeld> saveAllAndFlush(List<GameMeld> melds) {
        return repository.saveAllAndFlush(melds);
    }

    @Override public void deleteAllAndFlush(List<GameMeld> melds) {
        repository.deleteAllInBatch(melds);
        repository.flush();
    }

    @Override public List<GameMeld> findByGameId(long gameId) {
        return repository.findByGameIdOrderByPositionOrderAsc(gameId);
    }

    @Override public long countByGameId(long gameId) { return repository.countByGameId(gameId); }

    @Override public boolean existsByGameIdAndMeldId(long gameId, String meldId) {
        return repository.existsByGameIdAndMeldId(gameId, meldId);
    }

    @Override public int findNextPosition(long gameId) {
        return Math.addExact(repository.findMaxPosition(gameId), 1);
    }
}
