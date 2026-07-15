package com.realtimetilegame.auth.application;

import com.realtimetilegame.user.domain.User;

public record RefreshRotationResult(User user, String refreshToken) {
}
