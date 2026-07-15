package com.realtimetilegame.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "요청 권한이 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호 확인이 일치하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단된 사용자입니다."),
    USER_DELETED(HttpStatus.UNAUTHORIZED, "탈퇴한 사용자입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),
    INVALID_AVATAR_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 아바타입니다."),

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    ROOM_FULL(HttpStatus.CONFLICT, "방의 정원이 가득 찼습니다."),
    ROOM_ALREADY_PLAYING(HttpStatus.CONFLICT, "이미 게임이 진행 중인 방입니다."),
    ROOM_CLOSED(HttpStatus.CONFLICT, "종료된 방입니다."),
    USER_ALREADY_IN_ROOM(HttpStatus.CONFLICT, "이미 다른 대기방에 참가 중입니다."),
    ROOM_MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "해당 방 참가자만 요청할 수 있습니다."),
    ROOM_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "방장만 요청할 수 있습니다."),
    ROOM_MIN_PLAYERS_NOT_MET(HttpStatus.CONFLICT, "게임 시작에 필요한 최소 인원이 부족합니다."),
    ROOM_PLAYERS_NOT_READY(HttpStatus.CONFLICT, "모든 참가자가 준비해야 합니다."),
    INVALID_MAX_PLAYERS(HttpStatus.BAD_REQUEST, "최대 인원은 2명에서 4명이어야 합니다."),
    INVALID_GAME_MODE(HttpStatus.BAD_REQUEST, "현재 지원하지 않는 게임 모드입니다."),
    INVALID_TIME_LIMIT(HttpStatus.BAD_REQUEST, "턴 제한시간은 30초에서 300초여야 합니다."),
    PRIVATE_ROOM_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "비공개 방은 아직 지원하지 않습니다."),
    INVALID_ROOM_ACTION_ID(HttpStatus.BAD_REQUEST, "유효한 actionId가 필요합니다."),

    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다."),
    GAME_MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "해당 게임 참가자만 요청할 수 있습니다."),

    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "데이터베이스 연결을 확인해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 요청을 처리하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
