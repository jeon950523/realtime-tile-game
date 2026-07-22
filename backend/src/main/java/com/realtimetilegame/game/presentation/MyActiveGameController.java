package com.realtimetilegame.game.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.game.application.GameQueryService;
import com.realtimetilegame.game.application.dto.ActiveGameView;
import com.realtimetilegame.security.CurrentUser;

@RestController
@RequestMapping("/api/me")
public class MyActiveGameController {
    private final GameQueryService queryService;

    public MyActiveGameController(GameQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/active-game")
    public ApiResponse<ActiveGameView> activeGame(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(queryService.activeGame(CurrentUser.from(jwt).userId()));
    }
}
