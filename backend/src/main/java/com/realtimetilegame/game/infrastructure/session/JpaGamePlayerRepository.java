package com.realtimetilegame.game.infrastructure.session;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;

@Repository
public class JpaGamePlayerRepository implements GamePlayerRepository {
    private final SpringDataGamePlayerJpaRepository repository;

    public JpaGamePlayerRepository(SpringDataGamePlayerJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<GamePlayer> saveAllAndFlush(List<GamePlayer> players) {
        return repository.saveAllAndFlush(players);
    }
    @Override public List<GamePlayer> findByGameId(long gameId) { return repository.findByGameId(gameId); }
    @Override public Optional<GamePlayer> findByGameIdAndUserId(long gameId, long userId) {
        return repository.findByGameIdAndUserId(gameId, userId);
    }
    @Override public boolean existsByGameIdAndUserId(long gameId, long userId) {
        return repository.existsByGameIdAndUserId(gameId, userId);
    }
    @Override public boolean existsActiveByGameIdAndUserId(long gameId, long userId) {
        return repository.existsActiveByGameIdAndUserId(gameId, userId);
    }
    @Override public long countByGameId(long gameId) { return repository.countByGameId(gameId); }
}
