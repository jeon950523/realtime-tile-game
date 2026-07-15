package com.realtimetilegame.room.websocket;

public record RoomCommandReply(
    String eventType,
    String actionId,
    String code,
    String message,
    boolean recoverable,
    Object payload
) {
    public static RoomCommandReply accepted(String actionId, Object payload) {
        return new RoomCommandReply("ROOM_COMMAND_ACCEPTED", actionId, null, "요청을 처리했습니다.", false, payload);
    }
    public static RoomCommandReply gameStartAccepted(String actionId, Object payload) {
        return new RoomCommandReply("GAME_START_ACCEPTED", actionId, null, "게임을 시작했습니다.", false, payload);
    }
    public static RoomCommandReply rejected(String actionId, String code, String message) {
        return new RoomCommandReply("ROOM_COMMAND_REJECTED", actionId, code, message, true, null);
    }
    public RoomCommandReply duplicateReplay() {
        return new RoomCommandReply("DUPLICATE_ACTION_REPLAYED", actionId, code, message, recoverable, payload);
    }
}
