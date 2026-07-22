package com.realtimetilegame.security;

import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(long userId) {
    public static CurrentUser from(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("jwt must not be null");
        }
        return new CurrentUser(Long.parseLong(jwt.getSubject()));
    }
}
