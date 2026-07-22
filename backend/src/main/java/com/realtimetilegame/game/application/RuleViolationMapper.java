package com.realtimetilegame.game.application;

import java.util.Collection;

import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.RuleViolation;

final class RuleViolationMapper {
    private RuleViolationMapper() {
    }

    static ErrorCode toErrorCode(RuleViolation violation) {
        return switch (violation.code()) {
            case TILE_NOT_FOUND, TILE_NOT_OWNED, NO_RACK_TILE_USED -> ErrorCode.TILE_NOT_IN_RACK;
            case DUPLICATED_TILE -> ErrorCode.DUPLICATE_TILE_IN_TURN;
            case INITIAL_MELD_SCORE_TOO_LOW -> ErrorCode.INITIAL_MELD_SCORE_TOO_LOW;
            case INVALID_RUN, INVALID_GROUP, INVALID_MELD,
                 INVALID_JOKER_REPLACEMENT, RETRIEVED_JOKER_NOT_REUSED,
                 JOKER_RETRIEVAL_NOT_ALLOWED -> ErrorCode.INVALID_MELD;
            case INVALID_TABLE_LAYOUT -> containsInvalidMeld(violation)
                ? ErrorCode.INVALID_MELD
                : ErrorCode.INVALID_TABLE_LAYOUT;
            case TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD, MISSING_TILE -> ErrorCode.INVALID_TABLE_LAYOUT;
        };
    }

    private static boolean containsInvalidMeld(RuleViolation violation) {
        Object errors = violation.details().get("errors");
        if (!(errors instanceof Collection<?> collection)) return false;
        return collection.stream().anyMatch(value ->
            RuleErrorCode.INVALID_MELD.name().equals(value)
                || RuleErrorCode.INVALID_RUN.name().equals(value)
                || RuleErrorCode.INVALID_GROUP.name().equals(value)
        );
    }
}
