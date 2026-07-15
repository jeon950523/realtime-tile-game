package com.realtimetilegame.security;

public record AccessToken(String value, long expiresInSeconds) {
}
