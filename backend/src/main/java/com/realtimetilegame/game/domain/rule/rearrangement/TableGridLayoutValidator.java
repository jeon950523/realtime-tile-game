package com.realtimetilegame.game.domain.rule.rearrangement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TableGridLayoutValidator {
    public static final int ROWS = 18;
    public static final int COLUMNS = 18;

    public boolean isValid(List<MeldPlacement> placements) {
        if (placements == null) return false;
        Set<Cell> occupied = new HashSet<>();
        for (MeldPlacement placement : placements) {
            if (placement == null || placement.tileCount() <= 0
                || placement.gridRow() < 0 || placement.gridRow() >= ROWS
                || placement.gridColumn() < 0
                || placement.gridColumn() + placement.tileCount() > COLUMNS) {
                return false;
            }
            for (int offset = 0; offset < placement.tileCount(); offset++) {
                if (!occupied.add(new Cell(placement.gridRow(), placement.gridColumn() + offset))) {
                    return false;
                }
            }
        }
        return true;
    }

    public record MeldPlacement(String meldId, int tileCount, int gridRow, int gridColumn) {
    }

    private record Cell(int row, int column) {
    }
}
