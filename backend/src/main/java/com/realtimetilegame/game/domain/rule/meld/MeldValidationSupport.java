package com.realtimetilegame.game.domain.rule.meld;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class MeldValidationSupport {
    private MeldValidationSupport() {
    }

    static ValidationResult<List<Tile>> resolveTiles(List<TileId> tileIds, TileCatalog catalog) {
        if (new HashSet<>(tileIds).size() != tileIds.size()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.DUPLICATED_TILE,
                "The same physical tile cannot be used more than once in a meld.",
                Map.of("tileIds", tileIds.stream().map(TileId::value).toList())
            ));
        }
        List<Tile> tiles = new ArrayList<>(tileIds.size());
        List<String> missing = new ArrayList<>();
        for (TileId tileId : tileIds) {
            if (!catalog.contains(tileId)) {
                missing.add(tileId.value());
            } else {
                tiles.add(catalog.get(tileId));
            }
        }
        if (!missing.isEmpty()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.TILE_NOT_FOUND,
                "One or more tiles are not registered in the catalog.",
                Map.of("tileIds", List.copyOf(missing))
            ));
        }
        return ValidationResults.success(List.copyOf(tiles));
    }

    @SuppressWarnings("unchecked")
    static <T> ValidationFailure<T> castFailure(ValidationResult<?> result) {
        return new ValidationFailure<>(((ValidationFailure<?>) result).violations());
    }
}
