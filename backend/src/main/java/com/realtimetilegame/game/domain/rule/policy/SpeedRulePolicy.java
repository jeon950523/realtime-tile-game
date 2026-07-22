package com.realtimetilegame.game.domain.rule.policy;

public final class SpeedRulePolicy implements RulePolicy {
    @Override
    public GameMode gameMode() {
        return GameMode.SPEED;
    }

    @Override
    public boolean requiresInitialMeld() {
        return false;
    }

    @Override
    public int requiredInitialMeldScore() {
        return 0;
    }

    @Override
    public boolean allowsTableRearrangementBeforeInitialMeld() {
        return true;
    }

    @Override
    public boolean affectsRating() {
        return false;
    }
}
