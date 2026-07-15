package com.realtimetilegame.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.security.AccessToken;
import com.realtimetilegame.security.JwtTokenService;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        RefreshTokenService refreshTokenService,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String password, String passwordConfirm, String nickname) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNicknameIgnoreCase(normalizedNickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.register(
            normalizedEmail,
            passwordEncoder.encode(password),
            normalizedNickname,
            now()
        );
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicate(exception);
        }
    }

    @Transactional
    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        assertLoginAllowed(user);
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        AccessToken accessToken = jwtTokenService.issue(user);
        String refreshToken = refreshTokenService.issue(user);
        return new LoginResult(accessToken, refreshToken, AuthenticatedUserView.from(user));
    }

    @Transactional
    public ReissueResult reissue(String rawRefreshToken) {
        RefreshRotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        assertLoginAllowed(rotation.user());
        AccessToken accessToken = jwtTokenService.issue(rotation.user());
        return new ReissueResult(accessToken, rotation.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeIfPresent(rawRefreshToken);
    }

    private static void assertLoginAllowed(User user) {
        if (user.status() == UserStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
        if (user.status() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
    }

    private static BusinessException translateDuplicate(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("email") || normalized.contains("uk_users_email")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (normalized.contains("nickname") || normalized.contains("uk_users_nickname")) {
            return new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        throw exception;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeNickname(String nickname) {
        return nickname.trim();
    }

    public record LoginResult(
        AccessToken accessToken,
        String refreshToken,
        AuthenticatedUserView user
    ) {
    }

    public record ReissueResult(AccessToken accessToken, String refreshToken) {
    }
}
