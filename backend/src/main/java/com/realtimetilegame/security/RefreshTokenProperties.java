package com.realtimetilegame.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh")
public record RefreshTokenProperties(
    long tokenTtlSeconds,
    boolean cookieSecure,
    String cookieName
) {
    public RefreshTokenProperties {
        if (tokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("Refresh cookie name must not be blank");
        }
    }
}
