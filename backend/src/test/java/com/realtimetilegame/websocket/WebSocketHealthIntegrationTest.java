package com.realtimetilegame.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketHealthIntegrationTest {
    private WebSocketStompClient stompClient;

    @LocalServerPort
    private int port;

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void publishesHealthResponseOverStomp() throws Exception {
        ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(1);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        StompSession session = stompClient
            .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
            })
            .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);

        session.subscribe("/topic/system.health", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        session.send("/app/system.health.ping", new byte[0]);

        String payload = messages.poll(5, TimeUnit.SECONDS);
        assertThat(payload).contains("SYSTEM_HEALTH").contains("UP");
    }
}
