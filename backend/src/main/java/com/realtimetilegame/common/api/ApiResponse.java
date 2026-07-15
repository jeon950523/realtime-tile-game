package com.realtimetilegame.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(
    boolean success,
    T data,
    OffsetDateTime timestamp
) {
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, OffsetDateTime.now(DEFAULT_ZONE_ID));
    }
}
