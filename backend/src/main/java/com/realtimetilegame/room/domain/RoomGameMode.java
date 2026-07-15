package com.realtimetilegame.room.domain;

public enum RoomGameMode {
    CLASSIC,
    SPEED;

    public static RoomGameMode parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("gameMode must not be null");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported game mode", exception);
        }
    }
}
