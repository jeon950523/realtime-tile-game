package com.realtimetilegame.websocket.presentation.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record RealtimeHealthMessage(
    String type,
    String status,
    OffsetDateTime timestamp
) {
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static RealtimeHealthMessage up() {
        return new RealtimeHealthMessage("SYSTEM_HEALTH", "UP", OffsetDateTime.now(DEFAULT_ZONE_ID));
    }
}
