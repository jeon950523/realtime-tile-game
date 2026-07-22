package com.realtimetilegame.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public final class PublicAuthPathBearerTokenResolver implements BearerTokenResolver {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/reissue",
        "/api/auth/logout"
    );
    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        return delegate.resolve(request);
    }
}
