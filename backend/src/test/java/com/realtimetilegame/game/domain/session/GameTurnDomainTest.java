package com.realtimetilegame.game.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.rule.model.MeldType;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.user.domain.User;

import org.junit.jupiter.api.Test;

class GameTurnDomainTest {
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 7, 15, 13, 0);
    private static final String FIRST_TURN_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND_TURN_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    void drawAdvancesTheTurnAndResetsConsecutivePasses() {
        Fixture fixture = fixture();
        Game game = fixture.game();
        game.advanceAfterPass(fixture.second(), 2, SECOND_TURN_ID, STARTED_AT.plusSeconds(10), 120);

        game.advanceAfterDraw(
            fixture.owner(),
            1,
            "33333333-3333-4333-8333-333333333333",
            STARTED_AT.plusSeconds(20),
            120
        );

        assertThat(game.currentTurnUser()).isSameAs(fixture.owner());
        assertThat(game.currentTurnSeatOrder()).isEqualTo(1);
        assertThat(game.turnNumber()).isEqualTo(3);
        assertThat(game.currentTurnId()).isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(game.currentTurnStartedAt()).isEqualTo(STARTED_AT.plusSeconds(20));
        assertThat(game.currentTurnDeadlineAt()).isEqualTo(STARTED_AT.plusSeconds(140));
        assertThat(game.consecutivePassCount()).isZero();
        assertThat(game.updatedAt()).isEqualTo(STARTED_AT.plusSeconds(20));
    }

    @Test
    void invalidNextTurnDoesNotPartiallyMutateTheCurrentTurn() {
        Fixture fixture = fixture();
        Game game = fixture.game();

        assertThatThrownBy(() -> game.advanceAfterDraw(
            fixture.second(),
            2,
            "not-a-uuid",
            STARTED_AT.plusSeconds(10),
            120
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(game.currentTurnUser()).isSameAs(fixture.owner());
        assertThat(game.currentTurnSeatOrder()).isEqualTo(1);
        assertThat(game.turnNumber()).isEqualTo(1);
        assertThat(game.currentTurnId()).isEqualTo(FIRST_TURN_ID);
        assertThat(game.currentTurnStartedAt()).isEqualTo(STARTED_AT);
        assertThat(game.currentTurnDeadlineAt()).isEqualTo(STARTED_AT.plusSeconds(120));
        assertThat(game.consecutivePassCount()).isZero();
        assertThat(game.updatedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void onlyAnUnownedPoolTileCanMoveToARackInTheSameGame() {
        Fixture fixture = fixture();
        GamePlayer ownerPlayer = GamePlayer.snapshot(fixture.game(), fixture.owner(), 1, STARTED_AT);
        GameTile tile = GameTile.pool(fixture.game(), new TileId("RED-01-A"), 0, STARTED_AT);

        tile.drawTo(ownerPlayer, 14, STARTED_AT.plusSeconds(5));

        assertThat(tile.location()).isEqualTo(GameTileLocation.RACK);
        assertThat(tile.owner()).isSameAs(ownerPlayer);
        assertThat(tile.positionOrder()).isEqualTo(14);
        assertThat(tile.updatedAt()).isEqualTo(STARTED_AT.plusSeconds(5));
        assertThatThrownBy(() -> tile.drawTo(ownerPlayer, 15, STARTED_AT.plusSeconds(6)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void drawRejectsAPlayerFromAnotherGameWithoutChangingTheTile() {
        Fixture fixture = fixture();
        Fixture other = fixture("other-owner@example.com", "otherOwner", "other-second@example.com", "otherSecond");
        GamePlayer otherPlayer = GamePlayer.snapshot(other.game(), other.owner(), 1, STARTED_AT);
        GameTile tile = GameTile.pool(fixture.game(), new TileId("BLUE-01-A"), 0, STARTED_AT);

        assertThatThrownBy(() -> tile.drawTo(otherPlayer, 14, STARTED_AT.plusSeconds(5)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(tile.location()).isEqualTo(GameTileLocation.POOL);
        assertThat(tile.owner()).isNull();
        assertThat(tile.positionOrder()).isZero();
        assertThat(tile.updatedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void completeInitialMeldIsMonotonicAndIdempotent() {
        Fixture fixture = fixture();
        GamePlayer player = GamePlayer.snapshot(fixture.game(), fixture.owner(), 1, STARTED_AT);

        player.completeInitialMeld();
        player.completeInitialMeld();

        assertThat(player.initialMeldCompleted()).isTrue();
    }

    @Test
    void meldAdvancesTheTurnAndResetsConsecutivePasses() {
        Fixture fixture = fixture();
        fixture.game().advanceAfterPass(fixture.second(), 2, SECOND_TURN_ID, STARTED_AT.plusSeconds(5), 120);

        fixture.game().advanceAfterMeld(
            fixture.owner(), 1, "33333333-3333-4333-8333-333333333333", STARTED_AT.plusSeconds(10), 120
        );

        assertThat(fixture.game().currentTurnUser()).isSameAs(fixture.owner());
        assertThat(fixture.game().turnNumber()).isEqualTo(3);
        assertThat(fixture.game().consecutivePassCount()).isZero();
        assertThat(fixture.game().currentTurnDeadlineAt()).isEqualTo(STARTED_AT.plusSeconds(130));
    }

    @Test
    void meldTracksItsCreatorSeparatelyFromItsLastModifier() {
        Fixture fixture = fixture();
        GamePlayer creator = GamePlayer.snapshot(fixture.game(), fixture.owner(), 1, STARTED_AT);
        GamePlayer modifier = GamePlayer.snapshot(fixture.game(), fixture.second(), 2, STARTED_AT);
        GameMeld meld = GameMeld.committed(
            fixture.game(), "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 0, MeldType.RUN, 24,
            creator, STARTED_AT
        );

        assertThat(meld.createdBy()).isSameAs(creator);
        assertThat(meld.lastModifiedBy()).isSameAs(creator);

        meld.markLastModifiedBy(modifier, STARTED_AT.plusSeconds(3));

        assertThat(meld.createdBy()).isSameAs(creator);
        assertThat(meld.lastModifiedBy()).isSameAs(modifier);
        assertThat(meld.updatedAt()).isEqualTo(STARTED_AT.plusSeconds(3));
    }

    @Test
    void ownedRackTileCommitsToAMeldInTheSameGame() {
        Fixture fixture = fixture();
        GamePlayer ownerPlayer = GamePlayer.snapshot(fixture.game(), fixture.owner(), 1, STARTED_AT);
        GameMeld meld = GameMeld.committed(
            fixture.game(), "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 0, MeldType.RUN, 24,
            ownerPlayer, STARTED_AT
        );
        GameTile tile = GameTile.rack(fixture.game(), ownerPlayer, new TileId("RED-07-A"), 0, STARTED_AT);

        tile.commitToTable(meld, 1, STARTED_AT.plusSeconds(2));

        assertThat(tile.location()).isEqualTo(GameTileLocation.TABLE);
        assertThat(tile.owner()).isNull();
        assertThat(tile.meld()).isSameAs(meld);
        assertThat(tile.positionOrder()).isEqualTo(1);
    }

    private static Fixture fixture() {
        return fixture("owner@example.com", "owner", "second@example.com", "second");
    }

    private static Fixture fixture(String ownerEmail, String ownerNickname, String secondEmail, String secondNickname) {
        User owner = User.register(ownerEmail, "encoded", ownerNickname, STARTED_AT);
        User second = User.register(secondEmail, "encoded", secondNickname, STARTED_AT);
        Room room = Room.createClassic("테스트방", owner, 2, 120, STARTED_AT);
        Game game = Game.startClassic(room, owner, 1, FIRST_TURN_ID, STARTED_AT, 120);
        return new Fixture(owner, second, game);
    }

    private record Fixture(User owner, User second, Game game) {
    }
}
