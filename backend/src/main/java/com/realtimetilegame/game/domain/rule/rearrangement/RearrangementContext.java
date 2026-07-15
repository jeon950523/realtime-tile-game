package com.realtimetilegame.game.domain.rule.rearrangement;

import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.policy.RulePolicy;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;

import java.util.List;
import java.util.Objects;

public record RearrangementContext(
    TableState turnStartTable,
    TableState candidateTable,
    RackState turnStartRack,
    RackState candidateRack,
    boolean initialMeldCompleted,
    RulePolicy rulePolicy,
    List<ValidatedMeld> validatedCandidateMelds
) {
    public RearrangementContext {
        turnStartTable = Objects.requireNonNull(turnStartTable, "turnStartTable must not be null");
        candidateTable = Objects.requireNonNull(candidateTable, "candidateTable must not be null");
        turnStartRack = Objects.requireNonNull(turnStartRack, "turnStartRack must not be null");
        candidateRack = Objects.requireNonNull(candidateRack, "candidateRack must not be null");
        rulePolicy = Objects.requireNonNull(rulePolicy, "rulePolicy must not be null");
        validatedCandidateMelds = List.copyOf(Objects.requireNonNull(validatedCandidateMelds, "validatedCandidateMelds must not be null"));
    }
}
