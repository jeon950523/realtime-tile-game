package com.realtimetilegame.room.application.dto;

public record RoomStartEligibility(boolean startable, String blockReason, int playerCount) {
}
