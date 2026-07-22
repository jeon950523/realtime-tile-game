package com.realtimetilegame.room.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.application.RoomQueryService;
import com.realtimetilegame.room.application.dto.RoomDetail;
import com.realtimetilegame.room.application.dto.RoomPage;
import com.realtimetilegame.room.application.dto.RoomSummary;
import com.realtimetilegame.room.presentation.dto.CreateRoomRequest;
import com.realtimetilegame.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomCommandService commandService;
    private final RoomQueryService queryService;

    public RoomController(RoomCommandService commandService, RoomQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<RoomPage> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "WAITING") String status,
        @RequestParam(defaultValue = "CLASSIC") String gameMode,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(queryService.list(CurrentUser.from(jwt).userId(), status, gameMode, page, size));
    }

    @GetMapping("/quick-match")
    public ApiResponse<RoomSummary> quickMatch(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "CLASSIC") String gameMode
    ) {
        return ApiResponse.success(queryService.quickMatch(CurrentUser.from(jwt).userId(), gameMode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomDetail> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.success(commandService.create(
            CurrentUser.from(jwt).userId(), request.roomName(), request.maxPlayers(), request.gameMode(),
            request.turnTimeLimitSeconds(), request.isPublic()
        ));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long roomId) {
        return ApiResponse.success(queryService.detail(roomId, CurrentUser.from(jwt).userId()));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomDetail> join(@AuthenticationPrincipal Jwt jwt, @PathVariable long roomId) {
        return ApiResponse.success(commandService.join(roomId, CurrentUser.from(jwt).userId()));
    }

    @DeleteMapping("/{roomId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable long roomId) {
        commandService.leave(roomId, CurrentUser.from(jwt).userId());
    }
}
