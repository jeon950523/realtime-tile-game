package com.realtimetilegame.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}$",
        message = "비밀번호는 8~64자의 영문, 숫자, 특수문자를 포함해야 합니다."
    )
    String password,

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    String passwordConfirm,

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다.")
    String nickname
) {
    public RegisterRequest {
        email = email == null ? null : email.trim();
        nickname = nickname == null ? null : nickname.trim();
    }
}
