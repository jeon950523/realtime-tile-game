package com.realtimetilegame.room.event;

public record RoomEventEnvelope(String destination, RealtimeEvent event) {
}
