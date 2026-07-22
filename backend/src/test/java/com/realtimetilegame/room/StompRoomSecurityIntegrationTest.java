package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.security.AccessToken;
import com.realtimetilegame.security.JwtTokenService;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.websocket.auth.StompAuthenticationChannelInterceptor;
import com.realtimetilegame.websocket.auth.StompDestinationAuthorizationInterceptor;
import com.realtimetilegame.websocket.auth.StompPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StompRoomSecurityIntegrationTest {
    @Autowired StompAuthenticationChannelInterceptor authenticationInterceptor;
    @Autowired StompDestinationAuthorizationInterceptor authorizationInterceptor;
    @Autowired RoomCommandService commandService;
    @Autowired GameStartService gameStartService;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    private User owner;
    private User outsider;
    private long roomId;
    private StompPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        owner = user("owner@example.com", "owner");
        outsider = user("outsider@example.com", "outsider");
        roomId = commandService.create(owner.id(), "보안방", 4, "CLASSIC", 120, true).roomId();
        ownerPrincipal = principal(tokenService.issue(owner));
    }

    @Test
    void anonymousCanSendHealthPing() {
        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SEND, "/app/system.health.ping", null), null)).isNotNull();
    }

    @Test
    void anonymousCanSubscribeHealthTopic() {
        Message<?> connected = authenticationInterceptor.preSend(connect(null), null);
        assertThat(StompHeaderAccessor.wrap(connected).getUser()).isNull();
        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SUBSCRIBE, "/topic/system.health", null), null)).isNotNull();
    }

    @Test
    void authenticatedUserCanSubscribeLobby() {
        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SUBSCRIBE, "/topic/lobby/rooms", ownerPrincipal), null)).isNotNull();
    }

    @Test
    void memberCanSubscribeOwnRoom() {
        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId, ownerPrincipal), null)).isNotNull();
    }

    @Test
    void memberCanSendReadyAndStart() {
        for (String destination : List.of(
            "/app/rooms/" + roomId + "/ready",
            "/app/rooms/" + roomId + "/start"
        )) {
            assertThat(authorizationInterceptor.preSend(
                frame(StompCommand.SEND, destination, ownerPrincipal), null)).isNotNull();
        }
    }


    @Test
    void gameMemberCanSubscribeOwnGameAndNonMemberIsRejected() {
        User second = user("game-second@example.com", "gameSecond");
        commandService.join(roomId, second.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, second.id(), true);
        long gameId = gameStartService.startGame(roomId, owner.id()).gameId();

        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SUBSCRIBE, "/topic/games/" + gameId, ownerPrincipal), null)).isNotNull();

        StompPrincipal outsiderPrincipal = principal(tokenService.issue(outsider));
        assertDenied(StompCommand.SUBSCRIBE, "/topic/games/" + gameId,
            outsiderPrincipal, "GAME_MEMBERSHIP_REQUIRED");
    }

    @Test
    void gameMemberCanSendDrawPassCommitAndPreviewWhileANonMemberIsRejected() {
        User second = user("game-command-second@example.com", "gameCommandSecond");
        commandService.join(roomId, second.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, second.id(), true);
        long gameId = gameStartService.startGame(roomId, owner.id()).gameId();

        for (String destination : List.of(
            "/app/games/" + gameId + "/turn/draw",
            "/app/games/" + gameId + "/turn/pass",
            "/app/games/" + gameId + "/turn/commit",
            "/app/games/" + gameId + "/turn/preview",
            "/app/games/" + gameId + "/turn/preview/cancel",
            "/app/games/" + gameId + "/exit"
        )) {
            assertThat(authorizationInterceptor.preSend(
                frame(StompCommand.SEND, destination, ownerPrincipal), null)).isNotNull();
            assertDenied(
                StompCommand.SEND,
                destination,
                principal(tokenService.issue(outsider)),
                "GAME_MEMBERSHIP_REQUIRED"
            );
        }
    }

    @Test
    void blockedGameMemberCannotSendATurnCommand() {
        User second = user("blocked-game-second@example.com", "blockedGameSecond");
        commandService.join(roomId, second.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, second.id(), true);
        long gameId = gameStartService.startGame(roomId, owner.id()).gameId();
        owner.block(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(owner);

        assertDenied(
            StompCommand.SEND,
            "/app/games/" + gameId + "/turn/draw",
            ownerPrincipal,
            "USER_BLOCKED"
        );
    }

    @Test
    void clientCannotForgeGameTopicOrPrivateGameStateQueue() {
        assertDenied(StompCommand.SEND, "/topic/games/1", ownerPrincipal, "FORBIDDEN");
        assertDenied(StompCommand.SEND, "/user/queue/game-state", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void authenticatedUserCanSubscribePrivateGameStateQueue() {
        assertThat(authorizationInterceptor.preSend(
            frame(StompCommand.SUBSCRIBE, "/user/queue/game-state", ownerPrincipal), null)).isNotNull();
    }

    @Test
    void authenticatedUserCannotSendToLobbyTopic() {
        assertDenied(StompCommand.SEND, "/topic/lobby/rooms", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void roomMemberCannotSendToRoomTopic() {
        assertDenied(StompCommand.SEND, "/topic/rooms/" + roomId, ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void authenticatedUserCannotSendToUserReplyDestination() {
        assertDenied(StompCommand.SEND, "/user/queue/replies", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void authenticatedUserCannotSendToBrokerQueue() {
        assertDenied(StompCommand.SEND, "/queue/replies", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void anonymousCannotSendToArbitraryBrokerTopic() {
        assertDenied(StompCommand.SEND, "/topic/arbitrary", null, "FORBIDDEN");
    }

    @Test
    void authenticatedUserCannotSubscribeApplicationDestination() {
        assertDenied(StompCommand.SUBSCRIBE, "/app/rooms/" + roomId + "/ready", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void unknownTopicIsDeniedByDefault() {
        assertDenied(StompCommand.SUBSCRIBE, "/topic/lobby/unknown", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void unknownApplicationDestinationIsDeniedByDefault() {
        assertDenied(StompCommand.SEND, "/app/rooms/" + roomId + "/unknown", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void oversizedRoomDestinationIsDeniedByDefault() {
        assertDenied(StompCommand.SEND,
            "/app/rooms/999999999999999999999999999999999999/ready", ownerPrincipal, "FORBIDDEN");
    }

    @Test
    void anonymousLobbySubscriptionIsRejected() {
        assertDenied(StompCommand.SUBSCRIBE, "/topic/lobby/rooms", null, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void invalidJwtDoesNotDowngradeToAnonymous() {
        assertThatThrownBy(() -> authenticationInterceptor.preSend(connect("Bearer invalid-token"), null))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("AUTHENTICATION_REQUIRED");
    }

    @Test
    void nonMemberCannotSubscribeRoomOrSendReady() {
        StompPrincipal outsiderPrincipal = principal(tokenService.issue(outsider));
        for (Message<?> protectedFrame : List.of(
            frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId, outsiderPrincipal),
            frame(StompCommand.SEND, "/app/rooms/" + roomId + "/ready", outsiderPrincipal)
        )) {
            assertThatThrownBy(() -> authorizationInterceptor.preSend(protectedFrame, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("ROOM_MEMBERSHIP_REQUIRED");
        }
    }

    @Test
    void blockedUserIsRejectedOnEveryAuthenticatedDestinationUsingCurrentDatabaseStatus() {
        owner.block(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(owner);
        assertDenied(StompCommand.SEND, "/app/rooms/" + roomId + "/ready", ownerPrincipal, "USER_BLOCKED");
    }

    @Test
    void deletedUserIsRejectedOnEveryAuthenticatedDestinationUsingCurrentDatabaseStatus() {
        owner.delete(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(owner);
        assertDenied(StompCommand.SUBSCRIBE, "/topic/lobby/rooms", ownerPrincipal, "USER_DELETED");
    }

    @Test
    void memberCannotSendReadyToAnotherRoom() {
        User secondOwner = user("second-owner@example.com", "secondOwner");
        long otherRoomId = commandService.create(secondOwner.id(), "다른방", 4, "CLASSIC", 120, true).roomId();
        assertDenied(StompCommand.SEND, "/app/rooms/" + otherRoomId + "/ready",
            ownerPrincipal, "ROOM_MEMBERSHIP_REQUIRED");
    }

    @Test
    void expiredStompPrincipalIsRejected() {
        StompPrincipal expired = new StompPrincipal(owner.id(), clock.instant().minusSeconds(1));
        assertDenied(StompCommand.SUBSCRIBE, "/topic/lobby/rooms", expired, "AUTHENTICATION_REQUIRED");
    }

    private void assertDenied(StompCommand command, String destination, StompPrincipal principal, String code) {
        assertThatThrownBy(() -> authorizationInterceptor.preSend(frame(command, destination, principal), null))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining(code);
    }

    private StompPrincipal principal(AccessToken token) {
        Message<?> authenticated = authenticationInterceptor.preSend(connect("Bearer " + token.value()), null);
        return (StompPrincipal) StompHeaderAccessor.wrap(authenticated).getUser();
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now));
    }

    private static Message<?> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) accessor.setNativeHeader("Authorization", authorization);

        // The authentication interceptor sets the Principal on the CONNECT accessor.
        // Keep these test headers mutable to mirror Spring's inbound STOMP pipeline.
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> frame(StompCommand command, String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
