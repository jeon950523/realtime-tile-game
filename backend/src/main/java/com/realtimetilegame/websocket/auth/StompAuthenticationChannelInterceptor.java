package com.realtimetilegame.websocket.auth;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;
import com.realtimetilegame.websocket.error.StompAccessDeniedException;

@Component
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {
    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    public StompAuthenticationChannelInterceptor(JwtDecoder jwtDecoder, UserRepository userRepository) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message,
            StompHeaderAccessor.class
        );
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        List<String> values = accessor.getNativeHeader("Authorization");
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return message;
        }

        String authorization = values.get(0);
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(7));
            long userId = Long.parseLong(jwt.getSubject());
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED));

            requireActive(user);
            accessor.setUser(new StompPrincipal(userId, jwt.getExpiresAt()));
            return message;
        } catch (StompAccessDeniedException exception) {
            throw exception;
        } catch (JwtException | NumberFormatException exception) {
            throw new StompAccessDeniedException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }

    private static void requireActive(User user) {
        if (user.status() == UserStatus.BLOCKED) throw new StompAccessDeniedException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new StompAccessDeniedException(ErrorCode.USER_DELETED);
    }
}
