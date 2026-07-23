package com.realtimetilegame.game.application.dto;

import java.time.OffsetDateTime;

public record GameTerminatedPayload(
    long roomId,
    long gameId,
    long gameVersion,
    String roomStatus,
    String gameStatus,
    String terminationReason,
    Long exitedParticipantId,
    Long exitedUserId,
    Long winnerParticipantId,
    Long winnerUserId,
    OffsetDateTime serverTime
) {
}
