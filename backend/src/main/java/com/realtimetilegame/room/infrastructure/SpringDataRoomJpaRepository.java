package com.realtimetilegame.room.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.room.domain.Room;

interface SpringDataRoomJpaRepository extends JpaRepository<Room, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") long roomId);

    @Query("""
        select new com.realtimetilegame.room.infrastructure.JpaRoomSummaryView(
            r.id, r.roomName, owner.nickname, count(rp.id), r.maxPlayers,
            r.gameMode, r.turnTimeLimitSeconds, r.status, r.createdAt)
        from Room r
        join r.owner owner
        left join RoomPlayer rp on rp.room = r and rp.leftAt is null
        where r.status = com.realtimetilegame.room.domain.RoomStatus.WAITING
          and r.gameMode = com.realtimetilegame.room.domain.RoomGameMode.CLASSIC
          and r.publicRoom = true
        group by r.id, r.roomName, owner.nickname, r.maxPlayers, r.gameMode,
                 r.turnTimeLimitSeconds, r.status, r.createdAt
        order by r.createdAt desc, r.id desc
        """)
    List<JpaRoomSummaryView> findWaitingClassicRooms(Pageable pageable);

    @Query("""
        select count(r.id) from Room r
        where r.status = com.realtimetilegame.room.domain.RoomStatus.WAITING
          and r.gameMode = com.realtimetilegame.room.domain.RoomGameMode.CLASSIC
          and r.publicRoom = true
        """)
    long countWaitingClassicRooms();

    @Query("""
        select new com.realtimetilegame.room.infrastructure.JpaRoomSummaryView(
            r.id, r.roomName, owner.nickname, count(rp.id), r.maxPlayers,
            r.gameMode, r.turnTimeLimitSeconds, r.status, r.createdAt)
        from Room r
        join r.owner owner
        left join RoomPlayer rp on rp.room = r and rp.leftAt is null
        where r.status = com.realtimetilegame.room.domain.RoomStatus.WAITING
          and r.gameMode = com.realtimetilegame.room.domain.RoomGameMode.CLASSIC
          and r.publicRoom = true
          and not exists (
              select 1 from RoomPlayer mine where mine.user.id = :userId and mine.leftAt is null
          )
        group by r.id, r.roomName, owner.nickname, r.maxPlayers, r.gameMode,
                 r.turnTimeLimitSeconds, r.status, r.createdAt
        having count(rp.id) < r.maxPlayers
        order by count(rp.id) desc, r.createdAt asc, r.id asc
        """)
    List<JpaRoomSummaryView> findQuickMatch(@Param("userId") long userId, Pageable pageable);
}
