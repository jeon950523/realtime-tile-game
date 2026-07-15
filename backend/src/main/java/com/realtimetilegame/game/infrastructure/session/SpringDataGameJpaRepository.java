package com.realtimetilegame.game.infrastructure.session;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.game.domain.session.Game;

interface SpringDataGameJpaRepository extends JpaRepository<Game, Long> {
    @EntityGraph(attributePaths = {"room", "currentTurnUser"})
    @Query("select g from Game g where g.id = :gameId")
    Optional<Game> findDetailedById(@Param("gameId") long gameId);

    @EntityGraph(attributePaths = {"room", "currentTurnUser"})
    @Query("select g from Game g where g.room.id = :roomId")
    Optional<Game> findByRoomId(@Param("roomId") long roomId);

    @EntityGraph(attributePaths = {"room", "currentTurnUser"})
    @Query("""
        select g from Game g
        where g.status = com.realtimetilegame.game.domain.session.GameStatus.IN_PROGRESS
          and exists (
              select 1 from GamePlayer gp
              where gp.game = g and gp.user.id = :userId
          )
        """)
    Optional<Game> findActiveByUserId(@Param("userId") long userId);

    boolean existsByRoomId(long roomId);

    long countByRoomId(long roomId);
}
