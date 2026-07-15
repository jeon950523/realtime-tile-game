package com.realtimetilegame.user.domain;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;

public enum AvatarType {
    DEFAULT_01,
    DEFAULT_02,
    DEFAULT_03,
    DEFAULT_04;

    public static AvatarType from(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR_TYPE);
        }
        try {
            return AvatarType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR_TYPE);
        }
    }
}
