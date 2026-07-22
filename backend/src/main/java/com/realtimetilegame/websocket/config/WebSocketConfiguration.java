package com.realtimetilegame.websocket.config;

import com.realtimetilegame.config.CorsProperties;
import com.realtimetilegame.websocket.auth.StompAuthenticationChannelInterceptor;
import com.realtimetilegame.websocket.auth.StompDestinationAuthorizationInterceptor;
import com.realtimetilegame.websocket.error.SafeStompErrorHandler;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final CorsProperties corsProperties;
    private final StompAuthenticationChannelInterceptor authenticationInterceptor;
    private final StompDestinationAuthorizationInterceptor authorizationInterceptor;
    private final SafeStompErrorHandler errorHandler;

    public WebSocketConfiguration(CorsProperties corsProperties,
                                  StompAuthenticationChannelInterceptor authenticationInterceptor,
                                  StompDestinationAuthorizationInterceptor authorizationInterceptor,
                                  SafeStompErrorHandler errorHandler) {
        this.corsProperties = corsProperties;
        this.authenticationInterceptor = authenticationInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor, authorizationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(errorHandler);
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new));
    }
}
