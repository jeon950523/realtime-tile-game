package com.realtimetilegame.game.application;

import com.realtimetilegame.game.event.GameStartedCommittedEvent;

public interface GameEventPublisher {
    void publish(GameStartedCommittedEvent event);
}
