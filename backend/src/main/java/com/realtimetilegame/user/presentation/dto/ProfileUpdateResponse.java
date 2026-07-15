package com.realtimetilegame.user.presentation.dto;

import com.realtimetilegame.user.domain.User;

public record ProfileUpdateResponse(String nickname, String avatarType) {
    public static ProfileUpdateResponse from(User user) {
        return new ProfileUpdateResponse(user.nickname(), user.avatarType().name());
    }
}
