package com.realtimetilegame.security;

import java.io.IOException;

import com.realtimetilegame.common.error.ErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public final class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final RestSecurityErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authenticationException
    ) throws IOException, ServletException {
        errorWriter.write(response, ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
