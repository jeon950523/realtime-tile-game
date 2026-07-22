package com.realtimetilegame.room.application.dto;

import java.util.List;

public record RoomPage(List<RoomSummary> content, int page, int size, long totalElements) {
    public RoomPage {
        content = List.copyOf(content);
    }
}
