package com.realtimetilegame.game.application.dto;

public record ActiveGameView(
    boolean active,
    Long gameId,
    Long roomId,
    String status
) {
    public static ActiveGameView active(long gameId, long roomId, String status) {
        return new ActiveGameView(true, gameId, roomId, status);
    }

    public static ActiveGameView none() {
        return new ActiveGameView(false, null, null, null);
    }
}
