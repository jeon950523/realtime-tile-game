package com.realtimetilegame.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.common.error.ApiErrorResponse;
import com.realtimetilegame.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public final class RestSecurityErrorWriter {
    private final ObjectMapper objectMapper;

    public RestSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(errorCode));
    }
}
