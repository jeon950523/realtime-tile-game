package com.realtimetilegame.game.infrastructure.session;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;

interface SpringDataGameTileJpaRepository extends JpaRepository<GameTile, Long> {
    @EntityGraph(attributePaths = {"owner", "owner.user"})
    @Query("""
        select gt from GameTile gt
        where gt.game.id = :gameId
        order by gt.location asc, gt.owner.id asc, gt.positionOrder asc
        """)
    List<GameTile> findByGameId(@Param("gameId") long gameId);

    long countByGameId(long gameId);

    long countByGameIdAndLocation(long gameId, GameTileLocation location);
}
