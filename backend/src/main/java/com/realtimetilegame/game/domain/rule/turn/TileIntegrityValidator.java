package com.realtimetilegame.game.domain.rule.turn;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TileIntegrityValidator {
    public ValidationResult<TileIntegrityReport> validate(
        TileLocationState state,
        Set<TileId> canonicalTileIds
    ) {
        Set<TileId> canonical = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(canonicalTileIds));
        List<TileId> located = new ArrayList<>();
        located.addAll(state.tilePool().remainingTileIds());
        state.racks().values().forEach(rack -> located.addAll(rack.tileIds()));
        located.addAll(state.table().allTileIds());

        Map<TileId, Integer> counts = new LinkedHashMap<>();
        located.forEach(tileId -> counts.merge(tileId, 1, Integer::sum));
        List<RuleViolation> violations = new ArrayList<>();

        Set<TileId> unknown = new LinkedHashSet<>(counts.keySet());
        unknown.removeAll(canonical);
        if (!unknown.isEmpty()) {
            violations.add(new RuleViolation(
                RuleErrorCode.TILE_NOT_FOUND,
                "The candidate state contains an unregistered tileId.",
                Map.of("tileIds", unknown.stream().map(TileId::value).toList())
            ));
        }

        List<TileId> duplicated = counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
        if (!duplicated.isEmpty()) {
            violations.add(new RuleViolation(
                RuleErrorCode.DUPLICATED_TILE,
                "A physical tile exists in more than one candidate location.",
                Map.of("tileIds", duplicated.stream().map(TileId::value).toList())
            ));
        }

        Set<TileId> missing = new LinkedHashSet<>(canonical);
        missing.removeAll(counts.keySet());
        if (!missing.isEmpty()) {
            violations.add(new RuleViolation(
                RuleErrorCode.MISSING_TILE,
                "One or more canonical tiles are missing from the candidate state.",
                Map.of("tileIds", missing.stream().map(TileId::value).toList())
            ));
        }

        if (!violations.isEmpty()) {
            return ValidationResults.failure(violations);
        }
        return ValidationResults.success(new TileIntegrityReport(located.size(), counts.size()));
    }
}
