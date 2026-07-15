package com.realtimetilegame.auth.presentation;

import com.realtimetilegame.auth.application.AuthService;
import com.realtimetilegame.auth.infrastructure.RefreshTokenCookieManager;
import com.realtimetilegame.auth.presentation.dto.LoginRequest;
import com.realtimetilegame.auth.presentation.dto.LoginResponse;
import com.realtimetilegame.auth.presentation.dto.RegisterRequest;
import com.realtimetilegame.auth.presentation.dto.RegisterResponse;
import com.realtimetilegame.auth.presentation.dto.ReissueResponse;
import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.user.domain.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieManager cookieManager;

    public AuthController(AuthService authService, RefreshTokenCookieManager cookieManager) {
        this.authService = authService;
        this.cookieManager = cookieManager;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
            request.email(),
            request.password(),
            request.passwordConfirm(),
            request.nickname()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(RegisterResponse.from(user)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        cookieManager.write(response, result.refreshToken());
        return ApiResponse.success(LoginResponse.from(result));
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = cookieManager.read(request)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING));
        try {
            AuthService.ReissueResult result = authService.reissue(rawRefreshToken);
            cookieManager.write(response, result.refreshToken());
            return ApiResponse.success(ReissueResponse.from(result));
        } catch (BusinessException exception) {
            if (exception.errorCode() == ErrorCode.REFRESH_TOKEN_INVALID
                || exception.errorCode() == ErrorCode.REFRESH_TOKEN_EXPIRED) {
                cookieManager.clear(response);
            }
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        cookieManager.read(request).ifPresent(authService::logout);
        cookieManager.clear(response);
        return ResponseEntity.noContent().build();
    }
}
