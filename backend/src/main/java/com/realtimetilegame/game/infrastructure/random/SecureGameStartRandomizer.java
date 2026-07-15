package com.realtimetilegame.game.infrastructure.random;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.realtimetilegame.game.application.GameStartRandomizer;
import com.realtimetilegame.game.domain.tile.Tile;

@Component
public class SecureGameStartRandomizer implements GameStartRandomizer {
    private final SecureRandom random = new SecureRandom();

    @Override
    public List<Tile> shuffledTiles(List<Tile> tiles) {
        List<Tile> shuffled = new ArrayList<>(List.copyOf(tiles));
        Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled);
    }

    @Override
    public int firstPlayerIndex(int playerCount) {
        if (playerCount < 2 || playerCount > 4) {
            throw new IllegalArgumentException("playerCount must be between 2 and 4");
        }
        return random.nextInt(playerCount);
    }
}
