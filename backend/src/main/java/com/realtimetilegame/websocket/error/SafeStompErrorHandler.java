package com.realtimetilegame.websocket.error;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;

@Component
public class SafeStompErrorHandler extends StompSubProtocolErrorHandler {
    private static final String FALLBACK_BODY = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버에서 요청을 처리하지 못했습니다.\"}";

    private final ObjectMapper objectMapper;

    public SafeStompErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage,
                                                               Throwable exception) {
        ErrorCode errorCode = resolveErrorCode(exception);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.name());
        accessor.setContentType(MediaType.APPLICATION_JSON);
        return MessageBuilder.createMessage(body(errorCode), accessor.getMessageHeaders());
    }

    private byte[] body(ErrorCode errorCode) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("code", errorCode.name());
            payload.put("message", errorCode.defaultMessage());
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            return FALLBACK_BODY.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static ErrorCode resolveErrorCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof StompAccessDeniedException accessDenied) {
                return accessDenied.errorCode();
            }
            if (current instanceof BusinessException businessException) {
                return businessException.errorCode();
            }
            current = current.getCause();
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

}
