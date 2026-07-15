package com.realtimetilegame.room.application.dto;

public record ActiveRoomView(boolean active, Long roomId, String status) {
    public static ActiveRoomView none() { return new ActiveRoomView(false, null, null); }
    public static ActiveRoomView active(long roomId, String status) { return new ActiveRoomView(true, roomId, status); }
}
