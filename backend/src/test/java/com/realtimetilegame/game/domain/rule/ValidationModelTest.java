package com.realtimetilegame.game.domain.rule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ValidationModelTest {
    @Test
    void validationFailureViolationsCannotBeModifiedExternally() {
        List<RuleViolation> source = new ArrayList<>();
        source.add(new RuleViolation(RuleErrorCode.INVALID_MELD, "invalid", Map.of("key", "value")));
        ValidationFailure<Object> failure = new ValidationFailure<>(source);
        source.clear();

        assertThatThrownBy(() -> failure.violations().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> failure.violations().get(0).details().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
