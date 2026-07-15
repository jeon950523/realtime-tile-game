package com.realtimetilegame.room.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
    @NotBlank @Size(min = 2, max = 50) String roomName,
    @Min(2) @Max(4) int maxPlayers,
    @NotBlank String gameMode,
    @Min(30) @Max(300) int turnTimeLimitSeconds,
    @NotNull Boolean isPublic
) {
}
