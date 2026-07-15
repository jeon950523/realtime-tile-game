package com.realtimetilegame.room.event;

import java.time.OffsetDateTime;

public record RealtimeEvent(String eventType, OffsetDateTime occurredAt, Object payload) {
}
