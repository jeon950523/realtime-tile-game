package com.realtimetilegame.game.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.game.application.GameQueryService;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.security.CurrentUser;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameQueryService queryService;

    public GameController(GameQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{gameId}")
    public ApiResponse<GamePrivateState> state(@AuthenticationPrincipal Jwt jwt, @PathVariable long gameId) {
        return ApiResponse.success(queryService.privateState(gameId, CurrentUser.from(jwt).userId()));
    }
}
