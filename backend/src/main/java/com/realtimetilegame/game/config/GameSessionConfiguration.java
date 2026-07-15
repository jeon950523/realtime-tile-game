package com.realtimetilegame.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.realtimetilegame.game.domain.state.InitialTileDistributor;

@Configuration
public class GameSessionConfiguration {
    @Bean
    InitialTileDistributor initialTileDistributor() {
        return new InitialTileDistributor();
    }
}
