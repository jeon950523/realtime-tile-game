package com.realtimetilegame.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.realtimetilegame.game.domain.state.InitialTileDistributor;
import com.realtimetilegame.game.domain.rule.turn.TurnCommitValidator;

@Configuration
public class GameSessionConfiguration {
    @Bean
    InitialTileDistributor initialTileDistributor() {
        return new InitialTileDistributor();
    }

    @Bean
    TurnCommitValidator turnCommitValidator() {
        return new TurnCommitValidator();
    }
}
