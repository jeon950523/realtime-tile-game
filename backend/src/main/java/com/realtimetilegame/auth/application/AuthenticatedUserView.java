package com.realtimetilegame.auth.application;

import com.realtimetilegame.user.domain.User;

public record AuthenticatedUserView(
    long userId,
    String nickname,
    String avatarType,
    int ratingScore
) {
    public static AuthenticatedUserView from(User user) {
        return new AuthenticatedUserView(
            user.id(),
            user.nickname(),
            user.avatarType().name(),
            user.ratingScore()
        );
    }
}
