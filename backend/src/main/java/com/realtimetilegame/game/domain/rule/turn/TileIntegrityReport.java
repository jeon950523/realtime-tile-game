package com.realtimetilegame.game.domain.rule.turn;

public record TileIntegrityReport(int totalTileCount, int uniqueTileCount) {
    public TileIntegrityReport {
        if (totalTileCount < 0 || uniqueTileCount < 0) {
            throw new IllegalArgumentException("tile counts must not be negative");
        }
    }
}
