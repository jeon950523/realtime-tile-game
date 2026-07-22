package com.realtimetilegame.room.application.dto;

public record RoomStartAccepted(long roomId, boolean eligible, int playerCount, String message) {
}
