package com.realtimetilegame.game.websocket;

public record GameCommandReply(
    String eventType,
    String actionId,
    boolean accepted,
    boolean duplicate,
    String code,
    String message,
    long gameId,
    String actionType,
    long gameVersion
) {
    public static GameCommandReply accepted(String actionId, long gameId, String actionType, long gameVersion) {
        return new GameCommandReply(
            "GAME_COMMAND_ACCEPTED",
            actionId,
            true,
            false,
            null,
            "게임 행동을 처리했습니다.",
            gameId,
            actionType,
            gameVersion
        );
    }

    public static GameCommandReply rejected(String actionId, long gameId, String actionType,
                                            long gameVersion, String code, String message) {
        return new GameCommandReply(
            "GAME_COMMAND_REJECTED",
            actionId,
            false,
            false,
            code,
            message,
            gameId,
            actionType,
            gameVersion
        );
    }

    public GameCommandReply duplicateReplay() {
        return new GameCommandReply(
            "DUPLICATE_GAME_ACTION_REPLAYED",
            actionId,
            accepted,
            true,
            code,
            message,
            gameId,
            actionType,
            gameVersion
        );
    }
}
