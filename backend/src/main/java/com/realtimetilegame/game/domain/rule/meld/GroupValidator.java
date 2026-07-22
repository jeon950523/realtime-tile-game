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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupValidator implements MeldValidator {
    @Override
    public ValidationResult<ValidatedMeld> validate(MeldCandidate candidate, TileCatalog tileCatalog) {
        int size = candidate.tileIds().size();
        if (size < 3 || size > 4) {
            return invalid("A group must contain 3 or 4 tiles.", Map.of("size", size));
        }

        ValidationResult<List<Tile>> resolved = MeldValidationSupport.resolveTiles(candidate.tileIds(), tileCatalog);
        if (resolved.isFailure()) {
            return MeldValidationSupport.castFailure(resolved);
        }
        List<Tile> tiles = ((com.realtimetilegame.game.domain.rule.model.ValidationSuccess<List<Tile>>) resolved).value();
        List<NumberTile> numberTiles = tiles.stream().filter(NumberTile.class::isInstance).map(NumberTile.class::cast).toList();
        if (numberTiles.isEmpty()) {
            return invalid("A group made only of jokers has no deterministic number context.", Map.of());
        }

        int groupNumber = numberTiles.get(0).number();
        if (numberTiles.stream().anyMatch(tile -> tile.number() != groupNumber)) {
            return invalid("Every number tile in a group must have the same number.", Map.of());
        }
        Set<TileColor> usedColors = EnumSet.noneOf(TileColor.class);
        for (NumberTile numberTile : numberTiles) {
            if (!usedColors.add(numberTile.color())) {
                return invalid("A group cannot contain the same color twice.", Map.of("color", numberTile.color().name()));
            }
        }

        EnumSet<TileColor> availableColors = EnumSet.allOf(TileColor.class);
        availableColors.removeAll(usedColors);
        long jokerCount = tiles.stream().filter(JokerTile.class::isInstance).count();
        if (availableColors.size() < jokerCount) {
            return invalid("There are not enough unused colors for every joker.", Map.of());
        }

        List<TileColor> deterministicColors = Arrays.stream(TileColor.values())
            .filter(availableColors::contains)
            .toList();
        Map<TileId, JokerAssignment> assignments = new LinkedHashMap<>();
        int jokerIndex = 0;
        for (Tile tile : tiles) {
            if (tile instanceof JokerTile jokerTile) {
                TileColor resolvedColor = deterministicColors.get(jokerIndex++);
                assignments.put(jokerTile.id(), new JokerAssignment(
                    jokerTile.id(),
                    groupNumber,
                    resolvedColor,
                    availableColors
                ));
            }
        }

        return ValidationResults.success(new ValidatedMeld(
            candidate.meldId(),
            MeldType.GROUP,
            candidate.tileIds(),
            assignments,
            groupNumber * size
        ));
    }

    private static ValidationResult<ValidatedMeld> invalid(String message, Map<String, Object> details) {
        return ValidationResults.failure(new RuleViolation(RuleErrorCode.INVALID_GROUP, message, details));
    }
}
