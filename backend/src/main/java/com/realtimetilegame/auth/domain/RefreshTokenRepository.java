package com.realtimetilegame.auth.domain;

import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);
}
