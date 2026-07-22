package com.realtimetilegame.websocket.error;

import java.util.Objects;

import org.springframework.messaging.MessagingException;

import com.realtimetilegame.common.error.ErrorCode;

public final class StompAccessDeniedException extends MessagingException {
    private final ErrorCode errorCode;

    public StompAccessDeniedException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").name());
        this.errorCode = errorCode;
    }

    public StompAccessDeniedException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").name(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
