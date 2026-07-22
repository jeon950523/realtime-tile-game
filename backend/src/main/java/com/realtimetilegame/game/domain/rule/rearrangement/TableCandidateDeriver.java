package com.realtimetilegame.game.domain.rule.rearrangement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TableCandidateDeriver {
    public List<DerivedCandidate> derive(List<TilePlacement> placements) {
        if (placements == null) throw new IllegalArgumentException("placements are required");
        List<TilePlacement> ordered = placements.stream()
            .sorted(Comparator.comparingInt(TilePlacement::gridRow)
                .thenComparingInt(TilePlacement::gridColumn)
                .thenComparing(TilePlacement::tileId))
            .toList();
        List<DerivedCandidate> candidates = new ArrayList<>();
        List<TilePlacement> block = new ArrayList<>();
        for (TilePlacement placement : ordered) {
            if (!block.isEmpty()) {
                TilePlacement previous = block.get(block.size() - 1);
                if (previous.gridRow() != placement.gridRow()
                    || previous.gridColumn() + 1 != placement.gridColumn()) {
                    candidates.add(candidate(block));
                    block = new ArrayList<>();
                }
            }
            block.add(placement);
        }
        if (!block.isEmpty()) candidates.add(candidate(block));
        return List.copyOf(candidates);
    }

    private static DerivedCandidate candidate(List<TilePlacement> placements) {
        TilePlacement first = placements.get(0);
        return new DerivedCandidate(
            first.gridRow(), first.gridColumn(),
            placements.stream().map(TilePlacement::tileId).toList()
        );
    }

    public record TilePlacement(String tileId, int gridRow, int gridColumn) {
    }

    public record DerivedCandidate(int gridRow, int gridColumn, List<String> tileIds) {
        public DerivedCandidate {
            tileIds = List.copyOf(tileIds);
        }
    }
}
