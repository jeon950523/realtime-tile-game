package com.realtimetilegame.game.application;

import com.realtimetilegame.game.event.GameStartedCommittedEvent;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;

public interface GameEventPublisher {
    void publish(GameStartedCommittedEvent event);
    void publish(GameTurnCommittedEvent event);
}
