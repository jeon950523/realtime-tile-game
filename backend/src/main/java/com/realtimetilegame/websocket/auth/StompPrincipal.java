package com.realtimetilegame.websocket.auth;

import java.security.Principal;
import java.time.Instant;

public record StompPrincipal(long userId, Instant expiresAt) implements Principal {
    @Override public String getName() { return Long.toString(userId); }
}
