package com.realtimetilegame.security;

import java.io.IOException;
import java.util.Set;

import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class ActiveUserAuthorizationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/reissue",
        "/api/auth/logout"
    );

    private final UserRepository userRepository;
    private final RestSecurityErrorWriter errorWriter;

    public ActiveUserAuthorizationFilter(
        UserRepository userRepository,
        RestSecurityErrorWriter errorWriter
    ) {
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return "/api/health".equals(path)
            || path.startsWith("/ws/")
            || "/ws".equals(path)
            || PUBLIC_AUTH_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            reject(response, ErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            reject(response, ErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }
        if (user.status() == UserStatus.BLOCKED) {
            reject(response, ErrorCode.USER_BLOCKED);
            return;
        }
        if (user.status() == UserStatus.DELETED) {
            reject(response, ErrorCode.USER_DELETED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        errorWriter.write(response, errorCode);
    }
}
