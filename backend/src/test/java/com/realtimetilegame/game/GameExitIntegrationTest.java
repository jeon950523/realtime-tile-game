package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameExitService;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.GameTurnCommitService;
import com.realtimetilegame.game.application.GameTurnCommandService;
import com.realtimetilegame.game.application.dto.CommitTableMeldCommand;
import com.realtimetilegame.game.application.dto.CommitTilePlacementCommand;
import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.application.dto.GameExitResult;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GamePlayerStatus;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTerminationReason;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.room.event.RoomEventEnvelope;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class GameExitIntegrationTest {
    @Autowired GameExitService exitService;
    @Autowired GameTurnCommandService turnCommandService;
    @Autowired GameTurnCommitService turnCommitService;
    @Autowired GameStartService gameStartService;
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameRepository gameRepository;
    @Autowired GamePlayerRepository gamePlayerRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomPlayerRepository roomPlayerRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;
    @Autowired ApplicationEvents applicationEvents;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void twoPlayerExitForfeitsRequesterAwardsOpponentAndReleasesBothActiveRooms() {
        StartedGame fixture = startedGame("two", 2);
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        User requester = fixture.users().get(0);
        User opponent = fixture.users().get(1);

        GameExitResult result = exitService.exit(
            fixture.roomId(), fixture.gameId(), requester.id(), before.version()
        );

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(result.actionType()).isEqualTo(GameExitService.EXIT_ACTIVE_GAME);
        assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(after.terminationReason()).isEqualTo(GameTerminationReason.PLAYER_FORFEIT);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT winner_user_id FROM games WHERE id = ?", Long.class, fixture.gameId()
        )).isEqualTo(opponent.id());
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(status(fixture.gameId(), requester.id())).isEqualTo(GamePlayerStatus.FORFEITED);
        assertThat(status(fixture.gameId(), opponent.id())).isEqualTo(GamePlayerStatus.WINNER);
        assertReleasedAndCanCreateNewRooms(fixture.users());
        assertThat(applicationEvents.stream(RoomEventEnvelope.class)
            .map(envelope -> envelope.event().eventType()))
            .contains("ROOM_CLOSED", "ROOM_REMOVED");
    }

    @Test
    void threeAndFourPlayerExitAbortWithoutWinnerAndReleaseEveryone() {
        for (int playerCount : List.of(3, 4)) {
            DatabaseCleanup.clear(jdbcTemplate);
            StartedGame fixture = startedGame("multi" + playerCount, playerCount);
            Game before = gameRepository.findById(fixture.gameId()).orElseThrow();

            exitService.exit(
                fixture.roomId(), fixture.gameId(), fixture.users().get(0).id(), before.version()
            );

            Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
            assertThat(after.status()).isEqualTo(GameStatus.ABORTED);
            assertThat(after.terminationReason()).isEqualTo(GameTerminationReason.PLAYER_LEFT);
            assertThat(after.winnerUser()).isNull();
            assertThat(status(fixture.gameId(), fixture.users().get(0).id()))
                .isEqualTo(GamePlayerStatus.FORFEITED);
            assertThat(fixture.users().subList(1, fixture.users().size()))
                .allSatisfy(user -> assertThat(status(fixture.gameId(), user.id()))
                    .isEqualTo(GamePlayerStatus.ABORTED));
            assertReleasedAndCanCreateNewRooms(fixture.users());
        }
    }

    @Test
    void closedRoomMembershipNeverBlocksCreatingANewRoom() {
        User user = user("closed-membership@example.com", "ClosedMembership");
        long staleRoomId = roomCommandService.create(
            user.id(), "닫힌방", 2, "CLASSIC", 120, true
        ).roomId();
        jdbcTemplate.update(
            "UPDATE rooms SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            staleRoomId
        );

        assertThat(roomPlayerRepository.findActiveByUserId(user.id())).isEmpty();
        assertThat(roomCommandService.create(
            user.id(), "새정상방", 2, "CLASSIC", 120, true
        ).roomId()).isPositive();
    }

    @Test
    void nonParticipantRoomMismatchStaleVersionAndSecondExitAreRejectedWithoutCorruption() {
        StartedGame fixture = startedGame("reject", 2);
        User outsider = user("reject-outsider@example.com", "RejectOutsider");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();

        assertCode(() -> exitService.exit(
            fixture.roomId(), fixture.gameId(), outsider.id(), before.version()
        ), ErrorCode.GAME_MEMBERSHIP_REQUIRED);
        assertCode(() -> exitService.exit(
            fixture.roomId() + 999, fixture.gameId(), fixture.users().get(0).id(), before.version()
        ), ErrorCode.ROOM_GAME_MISMATCH);
        assertCode(() -> exitService.exit(
            fixture.roomId(), fixture.gameId(), fixture.users().get(0).id(), before.version() + 1
        ), ErrorCode.STALE_GAME_VERSION);

        exitService.exit(fixture.roomId(), fixture.gameId(), fixture.users().get(0).id(), before.version());
        assertCode(() -> exitService.exit(
            fixture.roomId(), fixture.gameId(), fixture.users().get(0).id(), before.version()
        ), ErrorCode.GAME_NOT_IN_PROGRESS);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT winner_user_id FROM games WHERE id = ?", Long.class, fixture.gameId()
        )).isEqualTo(fixture.users().get(1).id());
    }

    @Test
    void concurrentExitAndDrawCommitOnlyOneVersionAndPreserveAConsistentState() throws Exception {
        StartedGame fixture = startedGame("exit-draw", 2);
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> exit = executor.submit(() -> invoke(() -> exitService.exit(
                fixture.roomId(), fixture.gameId(), requesterId, before.version()
            )));
            Future<Object> draw = executor.submit(() -> invoke(() -> turnCommandService.draw(
                fixture.gameId(), requesterId, before.version()
            )));
            List<Object> results = List.of(exit.get(5, TimeUnit.SECONDS), draw.get(5, TimeUnit.SECONDS));
            assertThat(results.stream().filter(value -> !(value instanceof ErrorCode)).count()).isEqualTo(1);
            assertThat(results.stream().filter(ErrorCode.class::isInstance).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        if (after.status() == GameStatus.IN_PROGRESS) {
            assertThat(roomPlayerRepository.findActiveByUserId(requesterId)).isPresent();
        } else {
            assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(fixture.users()).allSatisfy(user ->
                assertThat(roomPlayerRepository.findActiveByUserId(user.id())).isEmpty());
        }
    }

    @Test
    void concurrentExitAndPassCommitExactlyOneCommandWithoutPartialTerminationOrTurnAdvance() throws Exception {
        StartedGame fixture = startedGame("exit-pass", 2);
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        moveEntirePoolToRack(fixture.gameId(), requesterId);

        List<Object> results = race(
            () -> exitService.exit(fixture.roomId(), fixture.gameId(), requesterId, before.version()),
            () -> turnCommandService.pass(fixture.gameId(), requesterId, before.version())
        );

        assertExactlyOneSuccessAndOneVersionOrTerminalRejection(results);
        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(tableTileCount(fixture.gameId())).isZero();
        assertThat(meldCount(fixture.gameId())).isZero();
        if (after.status() == GameStatus.IN_PROGRESS) {
            assertPlayingRoomInvariant(fixture);
            assertThat(after.turnNumber()).isEqualTo(before.turnNumber() + 1);
            assertThat(after.currentTurnId()).isNotEqualTo(before.currentTurnId());
            assertThat(after.consecutivePassCount()).isEqualTo(1);
        } else {
            assertTerminalRoomInvariant(fixture, after);
            assertThat(after.turnNumber()).isEqualTo(before.turnNumber());
            assertThat(after.currentTurnId()).isEqualTo(before.currentTurnId());
        }
    }

    @Test
    void concurrentExitAndCommitCommitExactlyOneCommandWithoutPartialTerminationOrTableCommit() throws Exception {
        StartedGame fixture = startedGame("exit-commit", 2);
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        List<String> exactThirty = List.of(
            "RED-07-A", "RED-08-A", "RED-09-A",
            "BLUE-01-A", "BLUE-02-A", "BLUE-03-A"
        );
        arrangeCommitRacks(fixture.gameId(), requesterId, exactThirty);
        CommitTurnCommand command = commitCommand(before.version(), exactThirty);

        List<Object> results = race(
            () -> exitService.exit(fixture.roomId(), fixture.gameId(), requesterId, before.version()),
            () -> turnCommitService.commit(fixture.gameId(), requesterId, command)
        );

        assertExactlyOneSuccessAndOneVersionOrTerminalRejection(results);
        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        if (after.status() == GameStatus.IN_PROGRESS) {
            assertPlayingRoomInvariant(fixture);
            assertThat(after.turnNumber()).isEqualTo(before.turnNumber() + 1);
            assertThat(after.currentTurnId()).isNotEqualTo(before.currentTurnId());
            assertThat(meldCount(fixture.gameId())).isEqualTo(2);
            assertThat(tableTileCount(fixture.gameId())).isEqualTo(6);
        } else {
            assertTerminalRoomInvariant(fixture, after);
            assertThat(after.turnNumber()).isEqualTo(before.turnNumber());
            assertThat(after.currentTurnId()).isEqualTo(before.currentTurnId());
            assertThat(meldCount(fixture.gameId())).isZero();
            assertThat(tableTileCount(fixture.gameId())).isZero();
        }
    }

    @Test
    void concurrentExitsChooseOneWinnerExactlyOnceAndCloseTheRoomExactlyOnce() throws Exception {
        StartedGame fixture = startedGame("exit-exit", 2);
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> invoke(() -> exitService.exit(
                fixture.roomId(), fixture.gameId(), fixture.users().get(0).id(), before.version()
            )));
            Future<Object> second = executor.submit(() -> invoke(() -> exitService.exit(
                fixture.roomId(), fixture.gameId(), fixture.users().get(1).id(), before.version()
            )));
            List<Object> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(results.stream().filter(GameExitResult.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(ErrorCode.class::isInstance).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(after.winnerUser()).isNotNull();
        assertThat(gamePlayerRepository.findByGameId(fixture.gameId()))
            .filteredOn(player -> player.participantStatus() == GamePlayerStatus.WINNER).hasSize(1);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.CLOSED);
    }

    private StartedGame startedGame(String prefix, int playerCount) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            users.add(user(prefix + index + "@example.com", prefix + "User" + index));
        }
        long roomId = roomCommandService.create(
            users.get(0).id(), "종료검증방", playerCount, "CLASSIC", 120, true
        ).roomId();
        for (int index = 1; index < users.size(); index++) roomCommandService.join(roomId, users.get(index).id());
        for (User user : users) roomCommandService.changeReady(roomId, user.id(), true);
        long gameId = gameStartService.startGame(roomId, users.get(0).id()).gameId();
        return new StartedGame(roomId, gameId, List.copyOf(users));
    }

    private void assertReleasedAndCanCreateNewRooms(List<User> users) {
        for (int index = 0; index < users.size(); index++) {
            User user = users.get(index);
            assertThat(roomPlayerRepository.findActiveByUserId(user.id())).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_players WHERE user_id = ? AND left_at IS NULL",
                Long.class,
                user.id()
            )).isZero();
            assertThat(gameRepository.findActiveByUserId(user.id())).isEmpty();
            assertThat(roomCommandService.create(
                user.id(), "새방" + index, 2, "CLASSIC", 120, true
            ).roomId()).isPositive();
        }
    }

    private List<Object> race(ThrowingSupplier firstAction, ThrowingSupplier secondAction) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> invoke(firstAction));
            Future<Object> second = executor.submit(() -> invoke(secondAction));
            return List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertExactlyOneSuccessAndOneVersionOrTerminalRejection(List<Object> results) {
        assertThat(results.stream().filter(value -> !(value instanceof ErrorCode)).count()).isEqualTo(1);
        assertThat(results.stream().filter(ErrorCode.class::isInstance).map(ErrorCode.class::cast))
            .singleElement()
            .isIn(ErrorCode.STALE_GAME_VERSION, ErrorCode.GAME_NOT_IN_PROGRESS);
    }

    private void assertPlayingRoomInvariant(StartedGame fixture) {
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(fixture.users()).allSatisfy(user -> {
            assertThat(roomPlayerRepository.findActiveByUserId(user.id())).isPresent();
            assertThat(status(fixture.gameId(), user.id())).isEqualTo(GamePlayerStatus.ACTIVE);
        });
        assertThat(gameRepository.findActiveByUserId(fixture.users().get(0).id())).isPresent();
        assertThat(gameRepository.findActiveByUserId(fixture.users().get(1).id())).isPresent();
    }

    private void assertTerminalRoomInvariant(StartedGame fixture, Game game) {
        assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.terminationReason()).isEqualTo(GameTerminationReason.PLAYER_FORFEIT);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(fixture.users()).allSatisfy(user -> {
            assertThat(roomPlayerRepository.findActiveByUserId(user.id())).isEmpty();
            assertThat(gameRepository.findActiveByUserId(user.id())).isEmpty();
        });
        assertThat(gamePlayerRepository.findByGameId(fixture.gameId()))
            .filteredOn(player -> player.participantStatus() == GamePlayerStatus.FORFEITED).hasSize(1);
        assertThat(gamePlayerRepository.findByGameId(fixture.gameId()))
            .filteredOn(player -> player.participantStatus() == GamePlayerStatus.WINNER).hasSize(1);
    }

    private void moveEntirePoolToRack(long gameId, long userId) {
        Long playerId = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseThrow().id();
        jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, "
                + "position_order = position_order + 14 WHERE game_id = ? AND location = 'POOL'",
            playerId, gameId
        );
    }

    private void arrangeCommitRacks(long gameId, long requesterUserId, List<String> requiredRequesterTiles) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId).stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder)).toList();
        GamePlayer requester = players.stream().filter(player -> player.user().id() == requesterUserId)
            .findFirst().orElseThrow();
        GamePlayer other = players.stream().filter(player -> !player.id().equals(requester.id()))
            .findFirst().orElseThrow();
        List<String> all = jdbcTemplate.queryForList(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? ORDER BY tile_id", String.class, gameId
        );
        LinkedHashSet<String> requesterRack = new LinkedHashSet<>(requiredRequesterTiles);
        all.stream().filter(tileId -> !requesterRack.contains(tileId))
            .limit(14 - requesterRack.size()).forEach(requesterRack::add);
        LinkedHashSet<String> otherRack = new LinkedHashSet<>();
        all.stream().filter(tileId -> !requesterRack.contains(tileId)).limit(14).forEach(otherRack::add);
        int poolPosition = 0;
        int requesterPosition = 0;
        int otherPosition = 0;
        for (String tileId : all) {
            if (requesterRack.contains(tileId)) {
                jdbcTemplate.update(
                    "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, "
                        + "position_order = ? WHERE game_id = ? AND tile_id = ?",
                    requester.id(), requesterPosition++, gameId, tileId
                );
            } else if (otherRack.contains(tileId)) {
                jdbcTemplate.update(
                    "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, "
                        + "position_order = ? WHERE game_id = ? AND tile_id = ?",
                    other.id(), otherPosition++, gameId, tileId
                );
            } else {
                jdbcTemplate.update(
                    "UPDATE game_tiles SET location = 'POOL', owner_game_player_id = NULL, game_meld_id = NULL, "
                        + "position_order = ? WHERE game_id = ? AND tile_id = ?",
                    poolPosition++, gameId, tileId
                );
            }
        }
    }

    private static CommitTurnCommand commitCommand(long version, List<String> tileIds) {
        List<CommitTableMeldCommand> melds = List.of(
            new CommitTableMeldCommand(UUID.randomUUID().toString(), tileIds.subList(0, 3)),
            new CommitTableMeldCommand(UUID.randomUUID().toString(), tileIds.subList(3, 6))
        );
        List<CommitTilePlacementCommand> placements = new ArrayList<>();
        for (int index = 0; index < melds.size(); index++) {
            CommitTableMeldCommand meld = melds.get(index);
            for (int position = 0; position < meld.tileIds().size(); position++) {
                placements.add(new CommitTilePlacementCommand(
                    meld.tileIds().get(position), 0, index * 13 + position
                ));
            }
        }
        return new CommitTurnCommand(UUID.randomUUID().toString(), version, placements, null);
    }

    private long meldCount(long gameId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_melds WHERE game_id = ?", Long.class, gameId
        );
    }

    private long tableTileCount(long gameId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND location = 'TABLE'", Long.class, gameId
        );
    }

    private GamePlayerStatus status(long gameId, long userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseThrow().participantStatus();
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private Object invoke(ThrowingSupplier action) {
        try {
            return action.get();
        } catch (BusinessException exception) {
            return exception.errorCode();
        }
    }

    private static void assertCode(ThrowingSupplier action, ErrorCode expected) {
        assertThatThrownBy(action::get)
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(expected);
    }

    private record StartedGame(long roomId, long gameId, List<User> users) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }
}
