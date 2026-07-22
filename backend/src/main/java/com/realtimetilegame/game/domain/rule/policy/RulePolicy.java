package com.realtimetilegame.game.domain.rule.policy;

public interface RulePolicy {
    GameMode gameMode();

    boolean requiresInitialMeld();

    int requiredInitialMeldScore();

    boolean allowsTableRearrangementBeforeInitialMeld();

    boolean affectsRating();
}
