package com.realtimetilegame.auth.presentation.dto;

import com.realtimetilegame.user.domain.User;

public record RegisterResponse(
    long userId,
    String email,
    String nickname,
    boolean profileSetupRequired
) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.id(), user.email(), user.nickname(), true);
    }
}
