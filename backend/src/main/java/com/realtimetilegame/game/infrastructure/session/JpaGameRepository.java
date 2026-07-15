package com.realtimetilegame.game.infrastructure.session;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameRepository;

@Repository
public class JpaGameRepository implements GameRepository {
    private final SpringDataGameJpaRepository repository;

    public JpaGameRepository(SpringDataGameJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Game save(Game game) { return repository.save(game); }
    @Override public Game saveAndFlush(Game game) { return repository.saveAndFlush(game); }
    @Override public Optional<Game> findById(long gameId) { return repository.findDetailedById(gameId); }
    @Override public Optional<Game> findByRoomId(long roomId) { return repository.findByRoomId(roomId); }
    @Override public Optional<Game> findActiveByUserId(long userId) { return repository.findActiveByUserId(userId); }
    @Override public boolean existsByRoomId(long roomId) { return repository.existsByRoomId(roomId); }
    @Override public long countByRoomId(long roomId) { return repository.countByRoomId(roomId); }
}
