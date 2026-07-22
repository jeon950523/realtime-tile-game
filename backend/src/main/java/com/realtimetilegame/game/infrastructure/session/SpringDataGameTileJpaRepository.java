package com.realtimetilegame.game.infrastructure.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;

import jakarta.persistence.LockModeType;

interface SpringDataGameTileJpaRepository extends JpaRepository<GameTile, Long> {
    @EntityGraph(attributePaths = {"owner", "owner.user", "meld"})
    @Query("""
        select gt from GameTile gt
        where gt.game.id = :gameId
        order by gt.location asc, gt.owner.id asc, gt.positionOrder asc
        """)
    List<GameTile> findByGameId(@Param("gameId") long gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GameTile> findFirstByGameIdAndLocationOrderByPositionOrderAsc(
        long gameId,
        GameTileLocation location
    );

    @Query("""
        select coalesce(max(gt.positionOrder), -1)
        from GameTile gt
        where gt.owner.id = :gamePlayerId
          and gt.location = com.realtimetilegame.game.domain.session.GameTileLocation.RACK
        """)
    int findMaxRackPosition(@Param("gamePlayerId") long gamePlayerId);

    long countByGameId(long gameId);

    long countByGameIdAndLocation(long gameId, GameTileLocation location);
}
