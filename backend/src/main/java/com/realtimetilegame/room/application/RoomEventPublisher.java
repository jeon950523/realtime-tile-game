package com.realtimetilegame.room.application;

import com.realtimetilegame.room.event.RoomEventEnvelope;

public interface RoomEventPublisher {
    void publish(RoomEventEnvelope event);
}
