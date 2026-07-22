package com.realtimetilegame.auth.presentation.dto;

import com.realtimetilegame.auth.application.AuthenticatedUserView;
import com.realtimetilegame.auth.application.AuthService;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    AuthenticatedUserView user,
    Redirect redirect
) {
    public static LoginResponse from(AuthService.LoginResult result) {
        return new LoginResponse(
            result.accessToken().value(),
            result.accessToken().expiresInSeconds(),
            result.user(),
            new Redirect("LOBBY", null, null)
        );
    }

    public record Redirect(String type, Long roomId, Long gameId) {
    }
}
