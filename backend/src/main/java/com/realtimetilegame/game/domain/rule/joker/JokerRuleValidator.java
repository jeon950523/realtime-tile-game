package com.realtimetilegame.game.domain.rule.joker;

import com.realtimetilegame.game.domain.rule.meld.CompositeMeldValidator;
import com.realtimetilegame.game.domain.rule.model.JokerAssignment;
import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.MeldType;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationResults;
import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.JokerTile;
import com.realtimetilegame.game.domain.tile.NumberTile;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JokerRuleValidator {
    private final CompositeMeldValidator meldValidator;

    public JokerRuleValidator() {
        this(new CompositeMeldValidator());
    }

    public JokerRuleValidator(CompositeMeldValidator meldValidator) {
        this.meldValidator = Objects.requireNonNull(meldValidator, "meldValidator must not be null");
    }

    public ValidationResult<JokerValidationResult> validate(JokerValidationContext context) {
        Map<MeldId, ValidatedMeld> validatedStartMelds = validateStartMelds(context);
        Map<MeldId, ValidatedMeld> validatedCandidateMelds = indexValidatedMelds(context.validatedCandidateMelds());
        Map<TileId, JokerPlacement> startPlacements = jokerPlacements(
            context.turnStartTable(),
            validatedStartMelds,
            context
        );
        Map<TileId, JokerPlacement> candidatePlacements = jokerPlacements(
            context.candidateTable(),
            validatedCandidateMelds,
            context
        );

        Set<TileId> retrieved = new LinkedHashSet<>();
        startPlacements.forEach((jokerId, startPlacement) -> {
            JokerPlacement candidatePlacement = candidatePlacements.get(jokerId);
            if (!preservesJokerContext(jokerId, startPlacement, candidatePlacement)) {
                retrieved.add(jokerId);
            }
        });

        if (retrieved.isEmpty()) {
            return ValidationResults.success(new JokerValidationResult(Set.of()));
        }
        if (!context.initialMeldCompleted()) {
            return ValidationResults.failure(new RuleViolation(
                RuleErrorCode.JOKER_RETRIEVAL_NOT_ALLOWED,
                "A player cannot retrieve or move a table joker before completing the initial meld.",
                Map.of("jokerTileIds", retrieved.stream().map(TileId::value).toList())
            ));
        }

        Set<TileId> consumedReplacementTileIds = new LinkedHashSet<>();
        for (TileId jokerId : retrieved) {
            JokerPlacement candidatePlacement = candidatePlacements.get(jokerId);
            if (candidatePlacement == null
                || context.candidateRack().contains(jokerId)
                || candidatePlacement.validatedMeld() == null
                || !candidatePlacement.validatedMeld().tileIds().contains(jokerId)) {
                return ValidationResults.failure(new RuleViolation(
                    RuleErrorCode.RETRIEVED_JOKER_NOT_REUSED,
                    "A retrieved joker must be reused in a valid candidate table meld during the same turn.",
                    Map.of("jokerTileId", jokerId.value())
                ));
            }

            JokerPlacement originalPlacement = startPlacements.get(jokerId);
            JokerAssignment originalAssignment = originalPlacement == null
                || originalPlacement.validatedMeld() == null
                ? null
                : originalPlacement.validatedMeld().jokerAssignments().get(jokerId);
            java.util.Optional<TileId> replacementTileId = originalAssignment == null
                ? java.util.Optional.empty()
                : findValidReplacement(
                    context,
                    originalPlacement.meldState(),
                    originalAssignment,
                    consumedReplacementTileIds
                );
            if (replacementTileId.isEmpty()) {
                return ValidationResults.failure(new RuleViolation(
                    RuleErrorCode.INVALID_JOKER_REPLACEMENT,
                    "The retrieved joker was not replaced by a matching number tile.",
                    Map.of(
                        "jokerTileId", jokerId.value(),
                        "assignedNumber", originalAssignment == null ? -1 : originalAssignment.assignedNumber(),
                        "replaceableColors", originalAssignment == null
                            ? List.of()
                            : originalAssignment.replaceableColors().stream().map(Enum::name).toList()
                    )
                ));
            }
            consumedReplacementTileIds.add(replacementTileId.orElseThrow());
        }
        return ValidationResults.success(new JokerValidationResult(retrieved));
    }

    private static boolean preservesJokerContext(
        TileId jokerId,
        JokerPlacement startPlacement,
        JokerPlacement candidatePlacement
    ) {
        if (startPlacement == null
            || candidatePlacement == null
            || startPlacement.validatedMeld() == null
            || candidatePlacement.validatedMeld() == null) {
            return false;
        }

        ValidatedMeld startMeld = startPlacement.validatedMeld();
        ValidatedMeld candidateMeld = candidatePlacement.validatedMeld();
        JokerAssignment startAssignment = startMeld.jokerAssignments().get(jokerId);
        JokerAssignment candidateAssignment = candidateMeld.jokerAssignments().get(jokerId);
        if (!Objects.equals(startAssignment, candidateAssignment)
            || startMeld.meldType() != candidateMeld.meldType()) {
                return false;
            }

        if (startMeld.meldType() == MeldType.RUN) {
            return isOrderedSubsequence(
                startPlacement.meldState().tileIds(),
                candidatePlacement.meldState().tileIds()
            );
        }

        Set<TileId> candidateTileIds = new LinkedHashSet<>(candidatePlacement.meldState().tileIds());
        return candidateTileIds.containsAll(startPlacement.meldState().tileIds());
    }

    private static boolean isOrderedSubsequence(List<TileId> expected, List<TileId> candidate) {
        int expectedIndex = 0;
        for (TileId tileId : candidate) {
            if (expectedIndex < expected.size() && expected.get(expectedIndex).equals(tileId)) {
                expectedIndex++;
            }
        }
        return expectedIndex == expected.size();
    }

    private static Map<MeldId, ValidatedMeld> indexValidatedMelds(List<ValidatedMeld> validatedMelds) {
        Map<MeldId, ValidatedMeld> indexed = new LinkedHashMap<>();
        for (ValidatedMeld meld : validatedMelds) {
            if (indexed.putIfAbsent(meld.meldId(), meld) != null) {
                throw new IllegalArgumentException("validated candidate meldIds must be unique");
            }
        }
        return java.util.Collections.unmodifiableMap(indexed);
    }

    private Map<MeldId, ValidatedMeld> validateStartMelds(JokerValidationContext context) {
        Map<MeldId, ValidatedMeld> result = new LinkedHashMap<>();
        for (MeldState meldState : context.turnStartTable().melds()) {
            ValidationResult<ValidatedMeld> validation = meldValidator.validate(
                MeldCandidate.from(meldState),
                context.tileCatalog()
            );
            if (validation instanceof com.realtimetilegame.game.domain.rule.model.ValidationSuccess<ValidatedMeld> success) {
                result.put(meldState.meldId(), success.value());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<TileId, JokerPlacement> jokerPlacements(
        TableState table,
        Map<MeldId, ValidatedMeld> validatedMelds,
        JokerValidationContext context
    ) {
        Map<TileId, JokerPlacement> placements = new LinkedHashMap<>();
        for (MeldState meld : table.melds()) {
            ValidatedMeld validatedMeld = validatedMelds.get(meld.meldId());
            if (validatedMeld != null && !validatedMeld.tileIds().equals(meld.tileIds())) {
                validatedMeld = null;
            }
            for (int index = 0; index < meld.tileIds().size(); index++) {
                TileId tileId = meld.tileIds().get(index);
                if (context.tileCatalog().contains(tileId) && context.tileCatalog().get(tileId) instanceof JokerTile) {
                    placements.put(tileId, new JokerPlacement(meld, validatedMeld, index));
                }
            }
        }
        return java.util.Collections.unmodifiableMap(placements);
    }

    private static java.util.Optional<TileId> findValidReplacement(
        JokerValidationContext context,
        MeldState originalMeld,
        JokerAssignment assignment,
        Set<TileId> consumedReplacementTileIds
    ) {
        Set<TileId> originalMeldTileIds = new LinkedHashSet<>(originalMeld.tileIds());
        Set<TileId> originalNonJokerTileIds = new LinkedHashSet<>();
        for (TileId tileId : originalMeld.tileIds()) {
            if (!(context.tileCatalog().get(tileId) instanceof JokerTile)) {
                originalNonJokerTileIds.add(tileId);
            }
        }

        for (MeldState candidateMeld : context.candidateTable().melds()) {
            boolean preservesOriginalContext = candidateMeld.tileIds().stream().anyMatch(originalNonJokerTileIds::contains);
            if (!preservesOriginalContext) {
                continue;
            }
            for (TileId candidateTileId : candidateMeld.tileIds()) {
                if (originalMeldTileIds.contains(candidateTileId) || consumedReplacementTileIds.contains(candidateTileId)) {
                    continue;
                }
                Tile tile = context.tileCatalog().get(candidateTileId);
                if (tile instanceof NumberTile numberTile
                    && numberTile.number() == assignment.assignedNumber()
                    && assignment.replaceableColors().contains(numberTile.color())) {
                    return java.util.Optional.of(candidateTileId);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private record JokerPlacement(
        MeldState meldState,
        ValidatedMeld validatedMeld,
        int jokerIndex
    ) {
    }
}
