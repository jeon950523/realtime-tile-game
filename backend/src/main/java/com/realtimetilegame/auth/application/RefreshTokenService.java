package com.realtimetilegame.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.auth.domain.RefreshToken;
import com.realtimetilegame.auth.domain.RefreshTokenRepository;
import com.realtimetilegame.auth.infrastructure.RefreshTokenGenerator;
import com.realtimetilegame.auth.infrastructure.RefreshTokenHasher;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.security.RefreshTokenProperties;
import com.realtimetilegame.user.domain.User;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final RefreshTokenGenerator generator;
    private final RefreshTokenHasher hasher;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(
        RefreshTokenRepository repository,
        RefreshTokenGenerator generator,
        RefreshTokenHasher hasher,
        RefreshTokenProperties properties,
        Clock clock
    ) {
        this.repository = repository;
        this.generator = generator;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        LocalDateTime now = now();
        String rawToken = generator.generate();
        repository.save(RefreshToken.issue(
            user,
            hasher.hash(rawToken),
            now.plusSeconds(properties.tokenTtlSeconds()),
            now
        ));
        return rawToken;
    }

    @Transactional
    public RefreshRotationResult rotate(String rawToken) {
        LocalDateTime now = now();
        RefreshToken current = repository.findByTokenHashForUpdate(hasher.hash(requireRawToken(rawToken)))
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (current.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (current.isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        current.revoke(now);
        String nextRawToken = generator.generate();
        repository.save(RefreshToken.issue(
            current.user(),
            hasher.hash(nextRawToken),
            now.plusSeconds(properties.tokenTtlSeconds()),
            now
        ));
        return new RefreshRotationResult(current.user(), nextRawToken);
    }

    @Transactional
    public void revokeIfPresent(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(hasher.hash(rawToken))
            .filter(token -> !token.isRevoked())
            .ifPresent(token -> token.revoke(now()));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String requireRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        return rawToken;
    }
}
