package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.turn.RackContributionValidator;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.tile.TileColor;

import java.util.List;

import org.junit.jupiter.api.Test;

class RackContributionValidatorTest {
    private final RackContributionValidator validator = new RackContributionValidator();

    @Test
    void rejectsCandidateRackContainingTileNotOwnedAtTurnStart() {
        var unexpected = id(TileColor.BLACK, 13);
        var result = validator.validate(
            RackState.empty(),
            new RackState(List.of(unexpected)),
            TableState.empty(),
            TableState.empty()
        );
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code())
            .contains(RuleErrorCode.TILE_NOT_OWNED);
    }

    @Test
    void rejectsRackTileRemovedWithoutPlacementOnCandidateTable() {
        var rackTile = id(TileColor.BLACK, 13);
        var result = validator.validate(
            new RackState(List.of(rackTile)),
            RackState.empty(),
            TableState.empty(),
            TableState.empty()
        );
        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<?>) result).violations()).extracting(v -> v.code())
            .contains(RuleErrorCode.MISSING_TILE);
    }
}
