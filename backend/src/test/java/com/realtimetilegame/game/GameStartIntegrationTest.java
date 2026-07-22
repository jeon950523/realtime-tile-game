package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameQueryService;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GameRackTileView;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GameStartIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired GameQueryService gameQueryService;
    @Autowired GameRepository gameRepository;
    @Autowired GamePlayerRepository gamePlayerRepository;
    @Autowired GameTileRepository gameTileRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @ParameterizedTest
    @CsvSource({"2,78", "3,64", "4,50"})
    void ownerStartsAnAtomicGameSnapshotForTwoThreeAndFourPlayers(int playerCount, int expectedPool) {
        StartedFixture fixture = readyRoom(playerCount);

        GameStartResult result = gameStartService.startGame(fixture.roomId(), fixture.users().get(0).id());

        assertThat(result.roomId()).isEqualTo(fixture.roomId());
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.turnNumber()).isEqualTo(1);
        assertThat(result.playerCount()).isEqualTo(playerCount);
        assertThat(fixture.users()).extracting(User::id).contains(result.currentTurnUserId());
        assertThat(result.currentTurnSeatOrder()).isBetween(1, playerCount);
        assertThat(gameRepository.countByRoomId(fixture.roomId())).isEqualTo(1);
        var startedGame = gameRepository.findById(result.gameId()).orElseThrow();
        assertThat(startedGame.version()).isZero();
        assertThat(startedGame.currentTurnId()).matches("^[0-9a-f-]{36}$");
        assertThat(startedGame.currentTurnStartedAt()).isEqualTo(startedGame.startedAt());
        assertThat(startedGame.currentTurnDeadlineAt()).isEqualTo(startedGame.startedAt().plusSeconds(120));
        assertThat(startedGame.consecutivePassCount()).isZero();
        assertThat(gamePlayerRepository.countByGameId(result.gameId())).isEqualTo(playerCount);
        assertThat(gameTileRepository.countByGameId(result.gameId())).isEqualTo(106);
        assertThat(gameTileRepository.countByGameIdAndLocation(result.gameId(), GameTileLocation.POOL))
            .isEqualTo(expectedPool);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);

        List<Integer> rackCounts = jdbcTemplate.queryForList(
            """
                SELECT COUNT(*)
                FROM game_tiles
                WHERE game_id = ? AND location = 'RACK'
                GROUP BY owner_game_player_id
                ORDER BY owner_game_player_id
                """,
            Integer.class,
            result.gameId()
        );
        assertThat(rackCounts).hasSize(playerCount).containsOnly(14);
        Integer distinctTiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT tile_id) FROM game_tiles WHERE game_id = ?",
            Integer.class,
            result.gameId()
        );
        assertThat(distinctTiles).isEqualTo(106);
    }

    @Test
    void privateStateContainsOnlyTheRequestersRackAndPublicRackCounts() {
        StartedFixture fixture = readyRoom(2);
        GameStartResult result = gameStartService.startGame(fixture.roomId(), fixture.users().get(0).id());

        GamePrivateState ownerState = gameQueryService.privateState(result.gameId(), fixture.users().get(0).id());
        GamePrivateState secondState = gameQueryService.privateState(result.gameId(), fixture.users().get(1).id());

        assertThat(ownerState.myRack()).hasSize(14);
        assertThat(secondState.myRack()).hasSize(14);
        assertThat(ownerState.myRack()).extracting(GameRackTileView::tileId)
            .doesNotContainAnyElementsOf(secondState.myRack().stream().map(GameRackTileView::tileId).toList());
        assertThat(ownerState.publicState().players()).hasSize(2)
            .allSatisfy(player -> assertThat(player.rackTileCount()).isEqualTo(14));
        assertThat(ownerState.publicState().tableMelds()).isEmpty();
        assertThat(ownerState.publicState().tilePoolCount()).isEqualTo(78);
    }

    @Test
    void activeGameRecoveryReturnsTheSameGameForEveryParticipant() {
        StartedFixture fixture = readyRoom(2);
        GameStartResult result = gameStartService.startGame(fixture.roomId(), fixture.users().get(0).id());

        fixture.users().forEach(user -> {
            var active = gameQueryService.activeGame(user.id());
            assertThat(active.active()).isTrue();
            assertThat(active.gameId()).isEqualTo(result.gameId());
            assertThat(active.roomId()).isEqualTo(fixture.roomId());
            assertThat(active.status()).isEqualTo("IN_PROGRESS");
        });
    }

    @Test
    void blockedAndDeletedParticipantsCannotQueryGameState() {
        StartedFixture blockedFixture = readyRoom(2, "blocked");
        GameStartResult blockedGame = gameStartService.startGame(
            blockedFixture.roomId(), blockedFixture.users().get(0).id()
        );
        User blocked = blockedFixture.users().get(0);
        blocked.block(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(blocked);
        assertBusinessCode(
            () -> gameQueryService.privateState(blockedGame.gameId(), blocked.id()),
            ErrorCode.USER_BLOCKED
        );

        DatabaseCleanup.clear(jdbcTemplate);
        StartedFixture deletedFixture = readyRoom(2, "deleted");
        GameStartResult deletedGame = gameStartService.startGame(
            deletedFixture.roomId(), deletedFixture.users().get(0).id()
        );
        User deleted = deletedFixture.users().get(1);
        deleted.delete(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(deleted);
        assertBusinessCode(
            () -> gameQueryService.privateState(deletedGame.gameId(), deleted.id()),
            ErrorCode.USER_DELETED
        );
    }

    @Test
    void nonMemberCannotReadPrivateGameState() {
        StartedFixture fixture = readyRoom(2);
        User outsider = user("outsider@example.com", "outsider");
        GameStartResult result = gameStartService.startGame(fixture.roomId(), fixture.users().get(0).id());

        assertBusinessCode(
            () -> gameQueryService.privateState(result.gameId(), outsider.id()),
            ErrorCode.GAME_MEMBERSHIP_REQUIRED
        );
    }

    @Test
    void startRequiresAtLeastTwoPlayersAndEveryoneReady() {
        User owner = user("owner@example.com", "owner");
        long roomId = roomCommandService.create(owner.id(), "조건방", 4, "CLASSIC", 120, true).roomId();

        assertBusinessCode(() -> gameStartService.startGame(roomId, owner.id()), ErrorCode.ROOM_MIN_PLAYERS_NOT_MET);
        User second = user("second@example.com", "second");
        roomCommandService.join(roomId, second.id());
        assertBusinessCode(() -> gameStartService.startGame(roomId, owner.id()), ErrorCode.ROOM_PLAYERS_NOT_READY);

        assertThat(gameRepository.countByRoomId(roomId)).isZero();
        assertThat(roomRepository.findById(roomId).orElseThrow().status()).isEqualTo(RoomStatus.WAITING);
    }

    @Test
    void nonOwnerCannotStartEvenWhenEveryoneIsReady() {
        StartedFixture fixture = readyRoom(2);

        assertBusinessCode(
            () -> gameStartService.startGame(fixture.roomId(), fixture.users().get(1).id()),
            ErrorCode.ROOM_OWNER_REQUIRED
        );
        assertThat(gameRepository.countByRoomId(fixture.roomId())).isZero();
    }

    @Test
    void closedRoomAndAlreadyPlayingRoomCannotStart() {
        User owner = user("closed-owner@example.com", "closedOwner");
        long closedRoomId = roomCommandService.create(owner.id(), "종료방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.leave(closedRoomId, owner.id());
        assertBusinessCode(() -> gameStartService.startGame(closedRoomId, owner.id()), ErrorCode.ROOM_CLOSED);

        StartedFixture playing = readyRoom(2, "playing");
        gameStartService.startGame(playing.roomId(), playing.users().get(0).id());
        assertBusinessCode(
            () -> gameStartService.startGame(playing.roomId(), playing.users().get(0).id()),
            ErrorCode.ROOM_ALREADY_PLAYING
        );
    }

    @Test
    void waitingRoomLeaveCommandIsBlockedAfterGameStart() {
        StartedFixture fixture = readyRoom(2);
        gameStartService.startGame(fixture.roomId(), fixture.users().get(0).id());

        assertBusinessCode(
            () -> roomCommandService.leave(fixture.roomId(), fixture.users().get(1).id()),
            ErrorCode.ROOM_ALREADY_PLAYING
        );
        assertThat(gamePlayerRepository.countByGameId(
            gameRepository.findByRoomId(fixture.roomId()).orElseThrow().id())).isEqualTo(2);
    }

    private StartedFixture readyRoom(int playerCount) {
        return readyRoom(playerCount, "fixture");
    }

    private StartedFixture readyRoom(int playerCount, String prefix) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            users.add(user(prefix + index + "@example.com", prefix + index));
        }
        long roomId = roomCommandService.create(
            users.get(0).id(), "게임시작방", playerCount, "CLASSIC", 120, true
        ).roomId();
        for (int index = 1; index < users.size(); index++) {
            roomCommandService.join(roomId, users.get(index).id());
        }
        users.forEach(user -> roomCommandService.changeReady(roomId, user.id(), true));
        return new StartedFixture(roomId, List.copyOf(users));
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private static void assertBusinessCode(ThrowingRunnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(expected);
    }

    private record StartedFixture(long roomId, List<User> users) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
