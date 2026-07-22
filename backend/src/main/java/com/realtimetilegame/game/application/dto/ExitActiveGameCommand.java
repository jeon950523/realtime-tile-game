package com.realtimetilegame.game.application.dto;

public record ExitActiveGameCommand(
    String actionId,
    Long gameVersion,
    Long roomId
) {
}
