package com.realtimetilegame.security;

import java.io.IOException;

import com.realtimetilegame.common.error.ErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public final class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final RestSecurityErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        errorWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
