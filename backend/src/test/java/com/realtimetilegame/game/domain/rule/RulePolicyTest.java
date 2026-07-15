package com.realtimetilegame.game.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.game.domain.rule.policy.SpeedRulePolicy;

import org.junit.jupiter.api.Test;

class RulePolicyTest {
    @Test
    void separatesClassicAndSpeedRules() {
        ClassicRulePolicy classic = new ClassicRulePolicy();
        SpeedRulePolicy speed = new SpeedRulePolicy();

        assertThat(classic.gameMode()).isEqualTo(GameMode.CLASSIC);
        assertThat(classic.requiresInitialMeld()).isTrue();
        assertThat(classic.requiredInitialMeldScore()).isEqualTo(30);
        assertThat(classic.allowsTableRearrangementBeforeInitialMeld()).isFalse();
        assertThat(classic.affectsRating()).isTrue();

        assertThat(speed.gameMode()).isEqualTo(GameMode.SPEED);
        assertThat(speed.requiresInitialMeld()).isFalse();
        assertThat(speed.requiredInitialMeldScore()).isZero();
        assertThat(speed.allowsTableRearrangementBeforeInitialMeld()).isTrue();
        assertThat(speed.affectsRating()).isFalse();
    }
}
