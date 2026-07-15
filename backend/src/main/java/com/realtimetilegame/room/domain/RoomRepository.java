package com.realtimetilegame.room.domain;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {
    Room save(Room room);
    Room saveAndFlush(Room room);
    Optional<Room> findById(long roomId);
    Optional<Room> findByIdForUpdate(long roomId);
    List<RoomSummaryView> findWaitingClassicRooms(long userId, int offset, int limit);
    long countWaitingClassicRooms();
    Optional<RoomSummaryView> findQuickMatch(long userId);
}
