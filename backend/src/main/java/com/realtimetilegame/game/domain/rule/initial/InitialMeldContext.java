package com.realtimetilegame.game.domain.rule.initial;

import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.policy.RulePolicy;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;

import java.util.List;
import java.util.Objects;

public record InitialMeldContext(
    ParticipantId currentParticipantId,
    RackState turnStartRack,
    RackState candidateRack,
    TableState turnStartTable,
    TableState candidateTable,
    List<ValidatedMeld> candidateMelds,
    boolean initialMeldCompleted,
    RulePolicy rulePolicy
) {
    public InitialMeldContext {
        currentParticipantId = Objects.requireNonNull(currentParticipantId, "currentParticipantId must not be null");
        turnStartRack = Objects.requireNonNull(turnStartRack, "turnStartRack must not be null");
        candidateRack = Objects.requireNonNull(candidateRack, "candidateRack must not be null");
        turnStartTable = Objects.requireNonNull(turnStartTable, "turnStartTable must not be null");
        candidateTable = Objects.requireNonNull(candidateTable, "candidateTable must not be null");
        candidateMelds = List.copyOf(Objects.requireNonNull(candidateMelds, "candidateMelds must not be null"));
        rulePolicy = Objects.requireNonNull(rulePolicy, "rulePolicy must not be null");
    }
}
