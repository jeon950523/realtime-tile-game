package com.realtimetilegame.game.domain.rule.turn;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class RackContributionValidator {
    public ValidationResult<Set<TileId>> validate(
        RackState turnStartRack,
        RackState candidateRack,
        TableState turnStartTable,
        TableState candidateTable
    ) {
        Set<TileId> startRackIds = new LinkedHashSet<>(turnStartRack.tileIds());
        Set<TileId> candidateRackIds = new LinkedHashSet<>(candidateRack.tileIds());

        Set<TileId> unexpectedRackIds = new LinkedHashSet<>(candidateRackIds);
        unexpectedRackIds.removeAll(startRackIds);
        if (!unexpectedRackIds.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_OWNED,
                "The candidate rack contains a tile that was not in the turn-start rack.",
                Map.of("tileIds", unexpectedRackIds.stream().map(TileId::value).toList())
            ));
        }

        Set<TileId> startTableIds = new LinkedHashSet<>(turnStartTable.allTileIds());
        Set<TileId> tableTilesMovedToRack = new LinkedHashSet<>(candidateRackIds);
        tableTilesMovedToRack.retainAll(startTableIds);
        if (!tableTilesMovedToRack.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_OWNED,
                "A tile from the public table cannot be stored in the current player's rack.",
                Map.of("tileIds", tableTilesMovedToRack.stream().map(TileId::value).toList())
            ));
        }

        Set<TileId> rackToTableTiles = new LinkedHashSet<>(startRackIds);
        rackToTableTiles.removeAll(candidateRackIds);
        if (rackToTableTiles.isEmpty()) {
            return ValidationResults.failure(RuleViolation.of(
                RuleErrorCode.NO_RACK_TILE_USED,
                "At least one tile from the current player's rack must be placed on the table."
            ));
        }

        Set<TileId> candidateTableIds = new LinkedHashSet<>(candidateTable.allTileIds());
        Set<TileId> removedButNotPlaced = new LinkedHashSet<>(rackToTableTiles);
        removedButNotPlaced.removeAll(candidateTableIds);
        if (!removedButNotPlaced.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.MISSING_TILE,
                "Every tile removed from the rack must exist on the candidate table.",
                Map.of("tileIds", removedButNotPlaced.stream().map(TileId::value).toList())
            ));
        }

        return ValidationResults.success(java.util.Collections.unmodifiableSet(new LinkedHashSet<>(rackToTableTiles)));
    }
}
