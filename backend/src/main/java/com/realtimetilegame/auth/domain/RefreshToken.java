package com.realtimetilegame.auth.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.realtimetilegame.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(User user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.tokenHash = requireHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static RefreshToken issue(User user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        return new RefreshToken(user, tokenHash, expiresAt, createdAt);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "now must not be null");
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now must not be null"));
    }

    public Long id() {
        return id;
    }

    public User user() {
        return user;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime revokedAt() {
        return revokedAt;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    private static String requireHash(String tokenHash) {
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a 64 character SHA-256 hex value");
        }
        return tokenHash;
    }
}
