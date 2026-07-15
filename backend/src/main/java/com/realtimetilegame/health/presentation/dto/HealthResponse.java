package com.realtimetilegame.health.presentation.dto;

public record HealthResponse(
    String application,
    String status,
    String database
) {
}
