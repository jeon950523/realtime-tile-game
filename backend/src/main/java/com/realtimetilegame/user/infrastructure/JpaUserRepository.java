package com.realtimetilegame.user.infrastructure;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserJpaRepository repository;

    public JpaUserRepository(SpringDataUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(long userId) {
        return repository.findById(userId);
    }

    @Override
    public Optional<User> findByIdForUpdate(long userId) {
        return repository.findByIdForUpdate(userId);
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByNicknameIgnoreCase(String nickname) {
        return repository.existsByNicknameIgnoreCase(nickname);
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }

    @Override
    public User saveAndFlush(User user) {
        return repository.saveAndFlush(user);
    }
}
