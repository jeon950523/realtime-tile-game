package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CATALOG;
import static com.realtimetilegame.game.support.RuleTestFixtures.candidate;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.meld.CompositeMeldValidator;
import com.realtimetilegame.game.domain.rule.model.MeldType;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.tile.TileColor;

import org.junit.jupiter.api.Test;

class CompositeMeldValidatorTest {
    private final CompositeMeldValidator validator = new CompositeMeldValidator();

    @Test
    void prefersRunWhenRunValidationSucceeds() {
        var result = validator.validate(candidate(
            "RUN", id(TileColor.RED, 3), id(TileColor.RED, 4), id(TileColor.RED, 5)
        ), CATALOG);
        assertThat(((ValidationSuccess<ValidatedMeld>) result).value().meldType()).isEqualTo(MeldType.RUN);
    }

    @Test
    void fallsBackToGroupWhenRunFails() {
        var result = validator.validate(candidate(
            "GROUP", id(TileColor.RED, 7), id(TileColor.BLUE, 7), id(TileColor.YELLOW, 7)
        ), CATALOG);
        assertThat(((ValidationSuccess<ValidatedMeld>) result).value().meldType()).isEqualTo(MeldType.GROUP);
    }

    @Test
    void returnsInvalidMeldWhenBothValidatorsFail() {
        var result = validator.validate(candidate(
            "INVALID", id(TileColor.RED, 3), id(TileColor.BLUE, 4), id(TileColor.YELLOW, 6)
        ), CATALOG);
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code())
            .containsExactly(RuleErrorCode.INVALID_MELD);
    }
}
