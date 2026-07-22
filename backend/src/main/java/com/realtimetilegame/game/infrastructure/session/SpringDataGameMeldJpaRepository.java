package com.realtimetilegame.game.infrastructure.session;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.game.domain.session.GameMeld;

interface SpringDataGameMeldJpaRepository extends JpaRepository<GameMeld, Long> {
    @EntityGraph(attributePaths = {"game", "createdBy", "createdBy.user"})
    List<GameMeld> findByGameIdOrderByPositionOrderAsc(long gameId);

    long countByGameId(long gameId);

    boolean existsByGameIdAndMeldId(long gameId, String meldId);

    @Query("select coalesce(max(gm.positionOrder), -1) from GameMeld gm where gm.game.id = :gameId")
    int findMaxPosition(@Param("gameId") long gameId);
}
