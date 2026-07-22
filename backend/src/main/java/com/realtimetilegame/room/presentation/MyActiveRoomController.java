package com.realtimetilegame.room.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.room.application.RoomQueryService;
import com.realtimetilegame.room.application.dto.ActiveRoomView;
import com.realtimetilegame.security.CurrentUser;

@RestController
@RequestMapping("/api/me")
public class MyActiveRoomController {
    private final RoomQueryService queryService;
    public MyActiveRoomController(RoomQueryService queryService) { this.queryService = queryService; }

    @GetMapping("/active-room")
    public ApiResponse<ActiveRoomView> activeRoom(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(queryService.activeRoom(CurrentUser.from(jwt).userId()));
    }
}
