package com.realtimetilegame.game.domain.rule.policy;

public final class ClassicRulePolicy implements RulePolicy {
    @Override
    public GameMode gameMode() {
        return GameMode.CLASSIC;
    }

    @Override
    public boolean requiresInitialMeld() {
        return true;
    }

    @Override
    public int requiredInitialMeldScore() {
        return 30;
    }

    @Override
    public boolean allowsTableRearrangementBeforeInitialMeld() {
        return false;
    }

    @Override
    public boolean affectsRating() {
        return true;
    }
}
