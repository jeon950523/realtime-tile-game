package com.realtimetilegame.websocket.auth;

import java.security.Principal;
import java.time.Instant;

public record StompPrincipal(long userId, Instant expiresAt, String sessionId) implements Principal {
    public StompPrincipal(long userId, Instant expiresAt) {
        this(userId, expiresAt, null);
    }

    @Override public String getName() { return Long.toString(userId); }
}
