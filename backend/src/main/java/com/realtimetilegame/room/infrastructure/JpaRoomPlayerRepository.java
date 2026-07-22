package com.realtimetilegame.room.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.realtimetilegame.room.domain.RoomPlayer;
import com.realtimetilegame.room.domain.RoomPlayerRepository;

@Repository
public class JpaRoomPlayerRepository implements RoomPlayerRepository {
    private final SpringDataRoomPlayerJpaRepository repository;

    public JpaRoomPlayerRepository(SpringDataRoomPlayerJpaRepository repository) {
        this.repository = repository;
    }

    @Override public RoomPlayer save(RoomPlayer player) { return repository.save(player); }
    @Override public RoomPlayer saveAndFlush(RoomPlayer player) { return repository.saveAndFlush(player); }
    @Override public List<RoomPlayer> saveAllAndFlush(List<RoomPlayer> players) { return repository.saveAllAndFlush(players); }
    @Override public Optional<RoomPlayer> findActiveByUserId(long userId) { return repository.findActiveByUserId(userId); }
    @Override public Optional<RoomPlayer> findActiveByRoomIdAndUserId(long roomId, long userId) { return repository.findActiveByRoomIdAndUserId(roomId, userId); }
    @Override public List<RoomPlayer> findActiveByRoomId(long roomId) { return repository.findActiveByRoomId(roomId); }
    @Override public List<RoomPlayer> findActiveByRoomIdForUpdate(long roomId) { return repository.findActiveByRoomIdForUpdate(roomId); }
    @Override public long countActiveByRoomId(long roomId) { return repository.countActiveByRoomId(roomId); }
    @Override public boolean existsActiveByRoomIdAndUserId(long roomId, long userId) { return repository.existsActiveByRoomIdAndUserId(roomId, userId); }
}
