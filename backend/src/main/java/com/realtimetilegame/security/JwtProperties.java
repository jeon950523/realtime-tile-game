package com.realtimetilegame.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(
    String issuer,
    String audience,
    long accessTokenTtlSeconds,
    String accessSecretBase64
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must not be blank");
        }
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        if (accessSecretBase64 == null || accessSecretBase64.isBlank()) {
            throw new IllegalArgumentException("JWT access secret environment variable is required");
        }
    }
}
