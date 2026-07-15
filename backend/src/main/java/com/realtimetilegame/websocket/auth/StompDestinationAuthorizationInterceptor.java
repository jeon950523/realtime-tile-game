package com.realtimetilegame.websocket.auth;

import java.security.Principal;
import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;
import com.realtimetilegame.websocket.error.StompAccessDeniedException;

@Component
public class StompDestinationAuthorizationInterceptor implements ChannelInterceptor {
    private static final String HEALTH_SEND = "/app/system.health.ping";
    private static final String HEALTH_SUBSCRIBE = "/topic/system.health";
    private static final String LOBBY_SUBSCRIBE = "/topic/lobby/rooms";
    private static final String REPLY_SUBSCRIBE = "/user/queue/replies";
    private static final String GAME_STATE_SUBSCRIBE = "/user/queue/game-state";
    private static final Pattern ROOM_SUBSCRIBE = Pattern.compile("^/topic/rooms/(\\d+)$");
    private static final Pattern ROOM_SEND = Pattern.compile("^/app/rooms/(\\d+)/(ready|start)$");
    private static final Pattern GAME_SUBSCRIBE = Pattern.compile("^/topic/games/(\\d+)$");

    private final UserRepository userRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final Clock clock;

    public StompDestinationAuthorizationInterceptor(UserRepository userRepository,
                                                     RoomPlayerRepository roomPlayerRepository,
                                                     GamePlayerRepository gamePlayerRepository,
                                                     Clock clock) {
        this.userRepository = userRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SEND) {
            authorizeSend(accessor.getDestination(), accessor.getUser());
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor.getDestination(), accessor.getUser());
        }
        return message;
    }

    private void authorizeSend(String destination, Principal principal) {
        if (HEALTH_SEND.equals(destination)) return;
        Long roomId = matchedPositiveId(ROOM_SEND, destination);
        if (roomId == null) throw forbidden();
        StompPrincipal authenticated = requireActivePrincipal(principal);
        requireRoomMembership(roomId, authenticated.userId());
    }

    private void authorizeSubscribe(String destination, Principal principal) {
        if (HEALTH_SUBSCRIBE.equals(destination)) return;
        if (LOBBY_SUBSCRIBE.equals(destination)
            || REPLY_SUBSCRIBE.equals(destination)
            || GAME_STATE_SUBSCRIBE.equals(destination)) {
            requireActivePrincipal(principal);
            return;
        }

        Long roomId = matchedPositiveId(ROOM_SUBSCRIBE, destination);
        if (roomId != null) {
            StompPrincipal authenticated = requireActivePrincipal(principal);
            requireRoomMembership(roomId, authenticated.userId());
            return;
        }

        Long gameId = matchedPositiveId(GAME_SUBSCRIBE, destination);
        if (gameId != null) {
            StompPrincipal authenticated = requireActivePrincipal(principal);
            requireGameMembership(gameId, authenticated.userId());
            return;
        }

        throw forbidden();
    }

    private StompPrincipal requireActivePrincipal(Principal principal) {
        StompPrincipal stompPrincipal = requirePrincipal(principal);
        if (stompPrincipal.expiresAt() == null || !stompPrincipal.expiresAt().isAfter(clock.instant())) {
            throw new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        User user = userRepository.findById(stompPrincipal.userId())
            .orElseThrow(() -> new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (user.status() == UserStatus.BLOCKED) throw new StompAccessDeniedException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new StompAccessDeniedException(ErrorCode.USER_DELETED);
        return stompPrincipal;
    }

    private void requireRoomMembership(long roomId, long userId) {
        if (!roomPlayerRepository.existsActiveByRoomIdAndUserId(roomId, userId)) {
            throw new StompAccessDeniedException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED);
        }
    }

    private void requireGameMembership(long gameId, long userId) {
        if (!gamePlayerRepository.existsActiveByGameIdAndUserId(gameId, userId)) {
            throw new StompAccessDeniedException(ErrorCode.GAME_MEMBERSHIP_REQUIRED);
        }
    }

    private static Long matchedPositiveId(Pattern pattern, String destination) {
        if (destination == null) return null;
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) return null;
        try {
            long value = Long.parseLong(matcher.group(1));
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static StompPrincipal requirePrincipal(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) return stompPrincipal;
        throw new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private static StompAccessDeniedException forbidden() {
        return new StompAccessDeniedException(ErrorCode.FORBIDDEN);
    }
}
