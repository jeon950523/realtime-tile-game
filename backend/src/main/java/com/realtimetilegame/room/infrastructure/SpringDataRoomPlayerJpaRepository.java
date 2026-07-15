package com.realtimetilegame.room.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.room.domain.RoomPlayer;

interface SpringDataRoomPlayerJpaRepository extends JpaRepository<RoomPlayer, Long> {
    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    @Query("select rp from RoomPlayer rp where rp.user.id = :userId and rp.leftAt is null")
    Optional<RoomPlayer> findActiveByUserId(@Param("userId") long userId);

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    @Query("select rp from RoomPlayer rp where rp.room.id = :roomId and rp.user.id = :userId and rp.leftAt is null")
    Optional<RoomPlayer> findActiveByRoomIdAndUserId(@Param("roomId") long roomId, @Param("userId") long userId);

    @EntityGraph(attributePaths = {"user"})
    @Query("select rp from RoomPlayer rp where rp.room.id = :roomId and rp.leftAt is null order by rp.seatOrder asc")
    List<RoomPlayer> findActiveByRoomId(@Param("roomId") long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user"})
    @Query("select rp from RoomPlayer rp where rp.room.id = :roomId and rp.leftAt is null order by rp.joinedAt asc, rp.id asc")
    List<RoomPlayer> findActiveByRoomIdForUpdate(@Param("roomId") long roomId);

    @Query("select count(rp.id) from RoomPlayer rp where rp.room.id = :roomId and rp.leftAt is null")
    long countActiveByRoomId(@Param("roomId") long roomId);

    @Query("select (count(rp.id) > 0) from RoomPlayer rp where rp.room.id = :roomId and rp.user.id = :userId and rp.leftAt is null")
    boolean existsActiveByRoomIdAndUserId(@Param("roomId") long roomId, @Param("userId") long userId);
}
