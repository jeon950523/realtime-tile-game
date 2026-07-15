package com.realtimetilegame.game.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.realtimetilegame.game.application.GameEventPublisher;

@Component
public class SpringGameEventPublisher implements GameEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringGameEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(GameStartedCommittedEvent event) {
        publisher.publishEvent(event);
    }
}
