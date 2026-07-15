package com.realtimetilegame.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ApiErrorResponse(
    boolean success,
    ErrorBody error,
    OffsetDateTime timestamp
) {
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(
            false,
            new ErrorBody(errorCode.name(), errorCode.defaultMessage(), List.of()),
            OffsetDateTime.now(DEFAULT_ZONE_ID)
        );
    }

    public static ApiErrorResponse validation(List<FieldErrorBody> fieldErrors) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return new ApiErrorResponse(
            false,
            new ErrorBody(errorCode.name(), errorCode.defaultMessage(), List.copyOf(fieldErrors)),
            OffsetDateTime.now(DEFAULT_ZONE_ID)
        );
    }

    public record ErrorBody(
        String code,
        String message,
        List<FieldErrorBody> fieldErrors
    ) {
    }

    public record FieldErrorBody(
        String field,
        String message
    ) {
    }
}
