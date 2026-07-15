package com.realtimetilegame.game.infrastructure.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.game.domain.session.GamePlayer;

interface SpringDataGamePlayerJpaRepository extends JpaRepository<GamePlayer, Long> {
    @EntityGraph(attributePaths = {"user"})
    @Query("select gp from GamePlayer gp where gp.game.id = :gameId order by gp.seatOrder asc")
    List<GamePlayer> findByGameId(@Param("gameId") long gameId);

    @EntityGraph(attributePaths = {"game", "game.room", "game.currentTurnUser", "user"})
    @Query("select gp from GamePlayer gp where gp.game.id = :gameId and gp.user.id = :userId")
    Optional<GamePlayer> findByGameIdAndUserId(@Param("gameId") long gameId, @Param("userId") long userId);

    @Query("select (count(gp.id) > 0) from GamePlayer gp where gp.game.id = :gameId and gp.user.id = :userId")
    boolean existsByGameIdAndUserId(@Param("gameId") long gameId, @Param("userId") long userId);

    @Query("""
        select (count(gp.id) > 0) from GamePlayer gp
        where gp.game.id = :gameId
          and gp.user.id = :userId
          and gp.game.status = com.realtimetilegame.game.domain.session.GameStatus.IN_PROGRESS
        """)
    boolean existsActiveByGameIdAndUserId(@Param("gameId") long gameId, @Param("userId") long userId);

    long countByGameId(long gameId);
}
