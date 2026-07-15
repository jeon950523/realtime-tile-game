package com.realtimetilegame.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다.")
    String nickname,

    @NotBlank(message = "아바타는 필수입니다.")
    String avatarType
) {
    public ProfileUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
        avatarType = avatarType == null ? null : avatarType.trim();
    }
}
