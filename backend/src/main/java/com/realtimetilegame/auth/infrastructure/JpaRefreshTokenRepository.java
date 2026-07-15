package com.realtimetilegame.auth.infrastructure;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.auth.domain.RefreshToken;
import com.realtimetilegame.auth.domain.RefreshTokenRepository;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {
    private final SpringDataRefreshTokenJpaRepository repository;

    public JpaRefreshTokenRepository(SpringDataRefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return repository.save(refreshToken);
    }
}
