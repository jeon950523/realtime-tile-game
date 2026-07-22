package com.realtimetilegame.game.domain.rule;

import static com.realtimetilegame.game.support.RuleTestFixtures.CANONICAL_IDS;
import static com.realtimetilegame.game.support.RuleTestFixtures.P1;
import static com.realtimetilegame.game.support.RuleTestFixtures.id;
import static com.realtimetilegame.game.support.RuleTestFixtures.meld;
import static com.realtimetilegame.game.support.RuleTestFixtures.state;
import static com.realtimetilegame.game.support.RuleTestFixtures.stateWithPool;
import static com.realtimetilegame.game.support.RuleTestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;

import com.realtimetilegame.game.domain.rule.model.RuleErrorCode;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.turn.TileIntegrityReport;
import com.realtimetilegame.game.domain.rule.turn.TileIntegrityValidator;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.tile.TileColor;
import com.realtimetilegame.game.domain.tile.TileId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TileIntegrityValidatorTest {
    private final TileIntegrityValidator validator = new TileIntegrityValidator();

    @Test
    void acceptsExactlyOneLocationForAll106CanonicalTiles() {
        TileLocationState state = state(TableState.empty(), id(TileColor.RED, 1));
        assertThat(validator.validate(state, CANONICAL_IDS).isSuccess()).isTrue();
    }

    @Test
    void reportsUnknownDuplicatedAndMissingTilesWithoutMutatingCandidate() {
        TileLocationState base = state(TableState.empty(), id(TileColor.RED, 1));
        List<TileId> pool = new ArrayList<>(base.tilePool().remainingTileIds());
        TileId missing = pool.remove(0);
        TileId duplicated = id(TileColor.RED, 1);
        TileId unknown = new TileId("UNKNOWN");
        TableState table = table(meld("INVALID-CANDIDATE", duplicated, unknown));
        TileLocationState candidate = stateWithPool(table, Map.of(P1, new RackState(List.of(duplicated))), pool);
        String before = candidate.toString();

        var result = validator.validate(candidate, CANONICAL_IDS);

        assertThat(result).isInstanceOf(ValidationFailure.class);
        assertThat(((ValidationFailure<TileIntegrityReport>) result).violations())
            .extracting(v -> v.code())
            .contains(RuleErrorCode.TILE_NOT_FOUND, RuleErrorCode.DUPLICATED_TILE, RuleErrorCode.MISSING_TILE);
        assertThat(candidate.toString()).isEqualTo(before);
        assertThat(missing).isNotNull();
    }
}
