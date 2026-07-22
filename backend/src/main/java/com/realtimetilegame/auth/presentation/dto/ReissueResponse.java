package com.realtimetilegame.auth.presentation.dto;

import com.realtimetilegame.auth.application.AuthService;

public record ReissueResponse(String accessToken, long expiresIn) {
    public static ReissueResponse from(AuthService.ReissueResult result) {
        return new ReissueResponse(result.accessToken().value(), result.accessToken().expiresInSeconds());
    }
}
