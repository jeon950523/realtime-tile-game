package com.realtimetilegame.game.domain.rule.meld;

import com.realtimetilegame.game.domain.rule.model.JokerAssignment;
import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.MeldType;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.tile.JokerTile;
import com.realtimetilegame.game.domain.tile.NumberTile;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunValidator implements MeldValidator {
    @Override
    public ValidationResult<ValidatedMeld> validate(MeldCandidate candidate, TileCatalog tileCatalog) {
        int size = candidate.tileIds().size();
        if (size < 3 || size > 13) {
            return invalid("A run must contain between 3 and 13 tiles.", Map.of("size", size));
        }

        ValidationResult<List<Tile>> resolved = MeldValidationSupport.resolveTiles(candidate.tileIds(), tileCatalog);
        if (resolved.isFailure()) {
            return MeldValidationSupport.castFailure(resolved);
        }
        List<Tile> tiles = ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<List<Tile>>) resolved).value();
        List<NumberTile> numberTiles = tiles.stream().filter(NumberTile.class::isInstance).map(NumberTile.class::cast).toList();
        if (numberTiles.isEmpty()) {
            return invalid("A run made only of jokers has no deterministic number or color context.", Map.of());
        }

        TileColor runColor = numberTiles.get(0).color();
        if (numberTiles.stream().anyMatch(tile -> tile.color() != runColor)) {
            return invalid("Every number tile in a run must have the same color.", Map.of());
        }

        Integer startNumber = null;
        for (int index = 0; index < tiles.size(); index++) {
            Tile tile = tiles.get(index);
            if (tile instanceof NumberTile numberTile) {
                int candidateStart = numberTile.number() - index;
                if (startNumber == null) {
                    startNumber = candidateStart;
                } else if (startNumber != candidateStart) {
                    return invalid("Run tiles must be consecutive in the submitted order.", Map.of("index", index));
                }
            }
        }
        int endNumber = startNumber + size - 1;
        if (startNumber < 1 || endNumber > 13) {
            return invalid("A run cannot continue below 1 or above 13.", Map.of(
                "startNumber", startNumber,
                "endNumber", endNumber
            ));
        }

        Map<TileId, JokerAssignment> assignments = new LinkedHashMap<>();
        int score = 0;
        for (int index = 0; index < tiles.size(); index++) {
            int assignedNumber = startNumber + index;
            Tile tile = tiles.get(index);
            if (tile instanceof JokerTile jokerTile) {
                assignments.put(jokerTile.id(), new JokerAssignment(
                    jokerTile.id(),
                    assignedNumber,
                    runColor,
                    java.util.Set.of(runColor)
                ));
            }
            score += assignedNumber;
        }

        return ValidationResults.success(new ValidatedMeld(
            candidate.meldId(),
            MeldType.RUN,
            candidate.tileIds(),
            assignments,
            score
        ));
    }

    private static ValidationResult<ValidatedMeld> invalid(String message, Map<String, Object> details) {
        return ValidationResults.failure(new RuleViolation(RuleErrorCode.INVALID_RUN, message, details));
    }
}
