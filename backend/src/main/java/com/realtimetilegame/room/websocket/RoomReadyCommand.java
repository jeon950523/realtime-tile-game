package com.realtimetilegame.room.websocket;

public record RoomReadyCommand(String actionId, boolean ready) {
}
