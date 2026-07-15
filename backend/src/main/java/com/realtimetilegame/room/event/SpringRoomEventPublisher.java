package com.realtimetilegame.room.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.realtimetilegame.room.application.RoomEventPublisher;

@Component
public class SpringRoomEventPublisher implements RoomEventPublisher {
    private final ApplicationEventPublisher publisher;
    public SpringRoomEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void publish(RoomEventEnvelope event) { publisher.publishEvent(event); }
}
