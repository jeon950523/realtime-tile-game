package com.realtimetilegame.room.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomSummaryView;

@Repository
public class JpaRoomRepository implements RoomRepository {
    private final SpringDataRoomJpaRepository repository;

    public JpaRoomRepository(SpringDataRoomJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Room save(Room room) { return repository.save(room); }
    @Override public Room saveAndFlush(Room room) { return repository.saveAndFlush(room); }
    @Override public Optional<Room> findById(long roomId) { return repository.findById(roomId); }
    @Override public Optional<Room> findByIdForUpdate(long roomId) { return repository.findByIdForUpdate(roomId); }
    @Override public long countWaitingClassicRooms() { return repository.countWaitingClassicRooms(); }

    @Override
    public List<RoomSummaryView> findWaitingClassicRooms(long userId, int offset, int limit) {
        int page = offset / limit;
        return List.copyOf(repository.findWaitingClassicRooms(PageRequest.of(page, limit)));
    }

    @Override
    public Optional<RoomSummaryView> findQuickMatch(long userId) {
        return repository.findQuickMatch(userId, PageRequest.of(0, 1)).stream().findFirst().map(value -> value);
    }
}
