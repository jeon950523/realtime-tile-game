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
import com.realtimetilegame.game.application.dto.TurnPreviewSnapshot;
import com.realtimetilegame.game.application.preview.TurnPreviewService;
import com.realtimetilegame.security.CurrentUser;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameQueryService queryService;
    private final TurnPreviewService turnPreviewService;

    public GameController(GameQueryService queryService, TurnPreviewService turnPreviewService) {
        this.queryService = queryService;
        this.turnPreviewService = turnPreviewService;
    }

    @GetMapping("/{gameId}")
    public ApiResponse<GamePrivateState> state(@AuthenticationPrincipal Jwt jwt, @PathVariable long gameId) {
        return ApiResponse.success(queryService.privateState(gameId, CurrentUser.from(jwt).userId()));
    }

    @GetMapping("/{gameId}/turn-preview")
    public ApiResponse<TurnPreviewSnapshot> turnPreview(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable long gameId) {
        return ApiResponse.success(
            turnPreviewService.current(gameId, CurrentUser.from(jwt).userId()).orElse(null)
        );
    }
}
