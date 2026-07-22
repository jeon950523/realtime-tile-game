package com.realtimetilegame.game.domain.rule.end;

import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.tile.JokerTile;
import com.realtimetilegame.game.domain.tile.NumberTile;
import com.realtimetilegame.game.domain.tile.Tile;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpeedScoreEvaluator {
    public SpeedScoreResult evaluate(SpeedScoreContext context) {
        Map<ParticipantId, Integer> finalScores = new LinkedHashMap<>();
        context.remainingRacks().keySet().stream()
            .sorted(Comparator.comparing(ParticipantId::value))
            .forEach(participant -> {
                int contributed = context.contributions().get(participant).stream()
                    .mapToInt(TileContribution::score)
                    .sum();
                int remaining = context.remainingRacks().get(participant).tileIds().stream()
                    .map(context.tileCatalog()::get)
                    .mapToInt(SpeedScoreEvaluator::remainingTileScore)
                    .sum();
                finalScores.put(participant, contributed - remaining);
            });

        int maximum = finalScores.values().stream().max(Integer::compareTo).orElseThrow();
        List<ParticipantId> leaders = finalScores.entrySet().stream()
            .filter(entry -> entry.getValue() == maximum)
            .map(Map.Entry::getKey)
            .toList();
        GameOutcomeCandidate outcome = leaders.size() == 1
            ? GameOutcomeCandidate.win(leaders.get(0))
            : GameOutcomeCandidate.draw();
        return new SpeedScoreResult(finalScores, outcome);
    }

    private static int remainingTileScore(Tile tile) {
        if (tile instanceof NumberTile numberTile) {
            return numberTile.number();
        }
        if (tile instanceof JokerTile) {
            return 30;
        }
        throw new IllegalArgumentException("unsupported tile type: " + tile.getClass().getName());
    }
}
