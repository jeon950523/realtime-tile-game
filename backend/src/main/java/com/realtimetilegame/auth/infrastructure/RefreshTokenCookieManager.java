package com.realtimetilegame.auth.infrastructure;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.realtimetilegame.security.RefreshTokenProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class RefreshTokenCookieManager {
    private static final String COOKIE_PATH = "/api/auth";
    private final RefreshTokenProperties properties;

    public RefreshTokenCookieManager(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> properties.cookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    public void write(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = baseCookie(rawToken)
            .maxAge(Duration.ofSeconds(properties.tokenTtlSeconds()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite("Strict")
            .path(COOKIE_PATH);
    }
}
