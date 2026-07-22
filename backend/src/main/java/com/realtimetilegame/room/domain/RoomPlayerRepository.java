package com.realtimetilegame.room.domain;

import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository {
    RoomPlayer save(RoomPlayer player);
    RoomPlayer saveAndFlush(RoomPlayer player);
    List<RoomPlayer> saveAllAndFlush(List<RoomPlayer> players);
    Optional<RoomPlayer> findActiveByUserId(long userId);
    Optional<RoomPlayer> findActiveByRoomIdAndUserId(long roomId, long userId);
    List<RoomPlayer> findActiveByRoomId(long roomId);
    List<RoomPlayer> findActiveByRoomIdForUpdate(long roomId);
    long countActiveByRoomId(long roomId);
    boolean existsActiveByRoomIdAndUserId(long roomId, long userId);
}
