package com.realtimetilegame.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.websocket.error.SafeStompErrorHandler;
import com.realtimetilegame.websocket.error.StompAccessDeniedException;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class SafeStompErrorHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SafeStompErrorHandler errorHandler = new SafeStompErrorHandler(objectMapper);

    @Test
    void stompErrorFrameContainsSafeCode() throws Exception {
        Message<byte[]> error = errorHandler.handleClientMessageProcessingError(clientMessage(),
            new StompAccessDeniedException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED));

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
        JsonNode body = objectMapper.readTree(error.getPayload());
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("ROOM_MEMBERSHIP_REQUIRED");
        assertThat(body.get("code").asText()).isEqualTo("ROOM_MEMBERSHIP_REQUIRED");
        assertThat(body.get("message").asText()).isEqualTo(ErrorCode.ROOM_MEMBERSHIP_REQUIRED.defaultMessage());
    }

    @Test
    void stompErrorFrameDoesNotContainStackTrace() {
        Message<byte[]> error = errorHandler.handleClientMessageProcessingError(clientMessage(),
            new RuntimeException("java.lang.IllegalStateException at com.secret.InternalService.execute"));

        String body = new String(error.getPayload(), StandardCharsets.UTF_8);
        assertThat(body).contains("INTERNAL_SERVER_ERROR");
        assertThat(body).doesNotContain("IllegalStateException", "InternalService", "execute");
    }

    @Test
    void stompErrorFrameDoesNotContainJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.secret.payload";
        Message<byte[]> error = errorHandler.handleClientMessageProcessingError(clientMessage(),
            new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED,
                new IllegalArgumentException("Authorization: Bearer " + jwt)));

        String body = new String(error.getPayload(), StandardCharsets.UTF_8);
        assertThat(body).contains("AUTHENTICATION_REQUIRED");
        assertThat(body).doesNotContain(jwt, "Authorization", "Bearer");
    }

    private static Message<byte[]> clientMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/lobby/rooms");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
