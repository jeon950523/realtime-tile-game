package com.realtimetilegame.auth.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.auth.domain.RefreshToken;

interface SpringDataRefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
