package com.realtimetilegame.common.error;

public final class ServiceUnavailableException extends RuntimeException {
    private final ErrorCode errorCode;

    public ServiceUnavailableException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
