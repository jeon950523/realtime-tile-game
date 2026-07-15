package com.realtimetilegame.user.presentation;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.security.CurrentUser;
import com.realtimetilegame.user.application.UserProfileService;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.presentation.dto.MyProfileResponse;
import com.realtimetilegame.user.presentation.dto.ProfileUpdateRequest;
import com.realtimetilegame.user.presentation.dto.ProfileUpdateResponse;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MyProfileController {
    private final UserProfileService profileService;

    public MyProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<MyProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        User user = profileService.get(CurrentUser.from(jwt).userId());
        return ApiResponse.success(MyProfileResponse.from(user));
    }

    @PatchMapping("/profile")
    public ApiResponse<ProfileUpdateResponse> update(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        User user = profileService.update(
            CurrentUser.from(jwt).userId(),
            request.nickname(),
            request.avatarType()
        );
        return ApiResponse.success(ProfileUpdateResponse.from(user));
    }
}
