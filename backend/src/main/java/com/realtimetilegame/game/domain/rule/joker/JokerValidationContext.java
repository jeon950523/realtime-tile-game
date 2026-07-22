package com.realtimetilegame.game.domain.rule.joker;

import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileCatalog;

import java.util.List;
import java.util.Objects;

public record JokerValidationContext(
    TableState turnStartTable,
    TableState candidateTable,
    RackState turnStartRack,
    RackState candidateRack,
    boolean initialMeldCompleted,
    TileCatalog tileCatalog,
    List<ValidatedMeld> validatedCandidateMelds
) {
    public JokerValidationContext {
        turnStartTable = Objects.requireNonNull(turnStartTable, "turnStartTable must not be null");
        candidateTable = Objects.requireNonNull(candidateTable, "candidateTable must not be null");
        turnStartRack = Objects.requireNonNull(turnStartRack, "turnStartRack must not be null");
        candidateRack = Objects.requireNonNull(candidateRack, "candidateRack must not be null");
        tileCatalog = Objects.requireNonNull(tileCatalog, "tileCatalog must not be null");
        validatedCandidateMelds = List.copyOf(Objects.requireNonNull(validatedCandidateMelds, "validatedCandidateMelds must not be null"));
    }
}
