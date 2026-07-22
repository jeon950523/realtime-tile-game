package com.realtimetilegame.game.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator;

/**
 * Keeps already-valid authoritative coordinates unchanged and projects only
 * legacy persisted layouts into the bounded 18x18 logical response space.
 */
final class PersistedTableGridLayoutResolver {
    private static final int CANDIDATE_SEPARATOR_COLUMNS = 1;

    Map<String, Coordinate> resolve(List<StoredPlacement> storedPlacements) {
        TableGridLayoutValidator validator = new TableGridLayoutValidator();
        List<TableGridLayoutValidator.MeldPlacement> persisted = storedPlacements.stream()
            .map(placement -> new TableGridLayoutValidator.MeldPlacement(
                placement.meldId(), placement.tileCount(), placement.gridRow(), placement.gridColumn()
            ))
            .toList();
        if (validator.isValid(persisted)) {
            Map<String, Coordinate> unchanged = new LinkedHashMap<>();
            storedPlacements.forEach(placement -> unchanged.put(
                placement.meldId(), new Coordinate(placement.gridRow(), placement.gridColumn())
            ));
            return Map.copyOf(unchanged);
        }

        Map<String, Coordinate> projected = new LinkedHashMap<>();
        int row = 0;
        int column = 0;
        for (StoredPlacement placement : storedPlacements) {
            if (placement.tileCount() <= 0 || placement.tileCount() > TableGridLayoutValidator.COLUMNS) {
                throw new IllegalStateException("persisted table meld cannot fit bounded grid");
            }
            if (column > 0 && column + placement.tileCount() > TableGridLayoutValidator.COLUMNS) {
                row++;
                column = 0;
            }
            if (row >= TableGridLayoutValidator.ROWS) {
                throw new IllegalStateException("persisted table layout cannot fit bounded grid");
            }
            projected.put(placement.meldId(), new Coordinate(row, column));
            column += placement.tileCount() + CANDIDATE_SEPARATOR_COLUMNS;
        }

        List<TableGridLayoutValidator.MeldPlacement> bounded = storedPlacements.stream()
            .map(placement -> {
                Coordinate coordinate = projected.get(placement.meldId());
                return new TableGridLayoutValidator.MeldPlacement(
                    placement.meldId(), placement.tileCount(), coordinate.gridRow(), coordinate.gridColumn()
                );
            })
            .toList();
        if (!validator.isValid(bounded)) {
            throw new IllegalStateException("persisted table layout projection must be valid");
        }
        return Map.copyOf(projected);
    }

    record StoredPlacement(String meldId, int tileCount, int gridRow, int gridColumn) {
    }

    record Coordinate(int gridRow, int gridColumn) {
    }
}
