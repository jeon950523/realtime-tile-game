package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.security.JwtTokenService;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompBrokerForgeryIntegrationTest {
    @Autowired RoomCommandService commandService;
    @Autowired GameStartService gameStartService;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SimpMessagingTemplate messagingTemplate;
    @Autowired Clock clock;

    @LocalServerPort int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void forgedLobbyEventIsRejectedAndNotDeliveredToAnotherClient() throws Exception {
        User owner = user("forgery-owner@example.com", "forgeryOwner");
        User attacker = user("forgery-attacker@example.com", "forgeryAttacker");
        long roomId = commandService.create(owner.id(), "위조차단방", 4, "CLASSIC", 120, true).roomId();

        ArrayBlockingQueue<String> receivedLobbyEvents = new ArrayBlockingQueue<>(10);
        ArrayBlockingQueue<String> observerErrors = new ArrayBlockingQueue<>(2);
        ArrayBlockingQueue<String> attackerErrors = new ArrayBlockingQueue<>(1);
        StompSession observerSession = connect(owner, errorHandler(observerErrors));
        subscribeAndAwaitRegistration(
            observerSession,
            "/topic/lobby/rooms",
            receivedLobbyEvents,
            observerErrors
        );
        commandService.join(roomId, attacker.id());
        assertThat(receivedLobbyEvents.poll(5, TimeUnit.SECONDS)).contains("ROOM_UPDATED");
        receivedLobbyEvents.clear();

        StompSession attackerSession = connect(attacker, errorHandler(attackerErrors));
        attackerSession.send("/topic/lobby/rooms",
            "{\"eventType\":\"ROOM_REMOVED\",\"payload\":{\"roomId\":999}}".getBytes(StandardCharsets.UTF_8));

        assertThat(attackerErrors.poll(5, TimeUnit.SECONDS)).contains("FORBIDDEN");
        assertThat(receivedLobbyEvents.poll(700, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void forgedGameEventIsRejectedAndNotDeliveredToAnotherMember() throws Exception {
        User owner = user("game-forgery-owner@example.com", "gameForgeryOwner");
        User attacker = user("game-forgery-attacker@example.com", "gameForgeryAttacker");
        long roomId = commandService.create(owner.id(), "게임이벤트위조차단", 2, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, attacker.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, attacker.id(), true);
        long gameId = gameStartService.startGame(roomId, owner.id()).gameId();

        ArrayBlockingQueue<String> receivedGameEvents = new ArrayBlockingQueue<>(10);
        ArrayBlockingQueue<String> observerErrors = new ArrayBlockingQueue<>(2);
        ArrayBlockingQueue<String> attackerErrors = new ArrayBlockingQueue<>(1);
        StompSession observerSession = connect(owner, errorHandler(observerErrors));
        subscribeAndAwaitRegistration(
            observerSession,
            "/topic/games/" + gameId,
            receivedGameEvents,
            observerErrors
        );
        receivedGameEvents.clear();

        StompSession attackerSession = connect(attacker, errorHandler(attackerErrors));
        attackerSession.send(
            "/topic/games/" + gameId,
            "{\"eventType\":\"GAME_STATE_UPDATED\",\"payload\":{}}"
                .getBytes(StandardCharsets.UTF_8)
        );

        assertThat(attackerErrors.poll(5, TimeUnit.SECONDS)).contains("FORBIDDEN");
        assertThat(receivedGameEvents.poll(700, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void forgedRoomEventIsRejectedAndNotDeliveredToAnotherMember() throws Exception {
        User owner = user("room-forgery-owner@example.com", "roomForgeryOwner");
        User attacker = user("room-forgery-attacker@example.com", "roomForgeryAttacker");
        long roomId = commandService.create(owner.id(), "방이벤트위조차단", 4, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, attacker.id());

        ArrayBlockingQueue<String> receivedRoomEvents = new ArrayBlockingQueue<>(10);
        ArrayBlockingQueue<String> observerErrors = new ArrayBlockingQueue<>(2);
        ArrayBlockingQueue<String> attackerErrors = new ArrayBlockingQueue<>(1);
        StompSession observerSession = connect(owner, errorHandler(observerErrors));
        subscribeAndAwaitRegistration(
            observerSession,
            "/topic/rooms/" + roomId,
            receivedRoomEvents,
            observerErrors
        );
        commandService.changeReady(roomId, owner.id(), true);
        assertThat(receivedRoomEvents.poll(5, TimeUnit.SECONDS)).contains("ROOM_READY_CHANGED");
        assertThat(receivedRoomEvents.poll(5, TimeUnit.SECONDS)).contains("ROOM_STATE_UPDATED");

        StompSession attackerSession = connect(attacker, errorHandler(attackerErrors));
        attackerSession.send("/topic/rooms/" + roomId,
            "{\"eventType\":\"ROOM_OWNER_CHANGED\",\"payload\":{\"userId\":999}}".getBytes(StandardCharsets.UTF_8));

        assertThat(attackerErrors.poll(5, TimeUnit.SECONDS)).contains("FORBIDDEN");
        assertThat(receivedRoomEvents.poll(700, TimeUnit.MILLISECONDS)).isNull();
    }

    private StompSession connect(User user, StompSessionHandlerAdapter handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenService.issue(user).value());
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                (WebSocketHttpHeaders) null,
                connectHeaders,
                handler)
            .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
    }

    private static StompSessionHandlerAdapter errorHandler(ArrayBlockingQueue<String> errors) {
        return new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String errorMessage = headers.getFirst("message");

                errors.offer(
                    (errorMessage == null ? "" : errorMessage)
                        + ":"
                        + new String((byte[]) payload, StandardCharsets.UTF_8)
                );
            }
        };
    }

    private static StompFrameHandler bytesHandler(ArrayBlockingQueue<String> messages) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        };
    }

    private void subscribeAndAwaitRegistration(
        StompSession session,
        String destination,
        ArrayBlockingQueue<String> messages,
        ArrayBlockingQueue<String> errors
    ) throws InterruptedException {
        session.subscribe(destination, bytesHandler(messages));

        String probeId = java.util.UUID.randomUUID().toString();
        String probePayload =
            "{\"eventType\":\"TEST_SUBSCRIPTION_PROBE\"," 
                + "\"payload\":{\"probeId\":\"" + probeId + "\"}}";

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (System.nanoTime() < deadline) {
            String error = errors.poll();
            if (error != null) {
                throw new AssertionError(
                    "STOMP subscription was rejected for " + destination + ": " + error
                );
            }

            messagingTemplate.convertAndSend(destination, probePayload);

            String received = messages.poll(100, TimeUnit.MILLISECONDS);
            if (received != null && received.contains(probeId)) {
                return;
            }
        }

        throw new AssertionError(
            "STOMP subscription was not registered in time: " + destination
        );
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now));
    }
}
