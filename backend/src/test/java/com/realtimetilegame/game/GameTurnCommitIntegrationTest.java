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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameQueryService;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.GameTurnCommandService;
import com.realtimetilegame.game.application.GameTurnCommitService;
import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.application.dto.CommitTableMeldCommand;
import com.realtimetilegame.game.application.dto.CommitTilePlacementCommand;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GameTerminatedPayload;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.MeldsCommittedPayload;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeldRepository;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GamePlayerStatus;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTerminationReason;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.event.RoomEventEnvelope;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@SpringBootTest
@ActiveProfiles("test")
@Import(GameTurnCommitIntegrationTest.CommitEventProbeConfiguration.class)
@RecordApplicationEvents
class GameTurnCommitIntegrationTest {
    private static final List<String> EXACT_THIRTY = List.of(
        "RED-07-A", "RED-08-A", "RED-09-A",
        "BLUE-01-A", "BLUE-02-A", "BLUE-03-A"
    );

    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired GameTurnCommitService commitService;
    @Autowired GameTurnCommandService turnCommandService;
    @Autowired GameQueryService queryService;
    @Autowired GameRepository gameRepository;
    @Autowired GamePlayerRepository playerRepository;
    @Autowired GameMeldRepository meldRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;
    @Autowired CommitEventProbe eventProbe;
    @Autowired ApplicationEvents applicationEvents;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
        eventProbe.reset();
    }

    @Test
    void commit001And003And016Through024_exactThirtyPersistsAtomicallyAndAdvances() {
        StartedGame fixture = startedGame("commit-success");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        GamePlayer requester = playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        GamePlayer next = nextPlayer(fixture.gameId(), before.currentTurnSeatOrder());
        arrangeRacks(fixture.gameId(), requesterId, EXACT_THIRTY);
        String firstMeldId = UUID.randomUUID().toString();
        String secondMeldId = UUID.randomUUID().toString();

        GameTurnCommandResult result = commitService.commit(
            fixture.gameId(), requesterId,
            command(before.version(),
                meld(firstMeldId, EXACT_THIRTY.subList(0, 3)),
                meld(secondMeldId, EXACT_THIRTY.subList(3, 6)))
        );

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(result.actionType()).isEqualTo("COMMIT");
        assertThat(result.gameVersion()).isEqualTo(before.version() + 1);
        assertThat(after.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(after.terminationReason()).isNull();
        assertThat(after.winnerUser()).isNull();
        assertThat(after.currentTurnUser().id()).isEqualTo(next.user().id());
        assertThat(after.turnNumber()).isEqualTo(before.turnNumber() + 1);
        assertThat(after.consecutivePassCount()).isZero();
        assertThat(meldRepository.countByGameId(fixture.gameId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND location = 'TABLE'", Long.class,
            fixture.gameId())).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? AND location = 'RACK'",
            Long.class, fixture.gameId(), requester.id())).isEqualTo(8L);
        assertThat(playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow()
            .initialMeldCompleted()).isTrue();

        GamePrivateState restored = queryService.privateState(fixture.gameId(), requesterId);
        assertThat(restored.publicState().tableMelds()).hasSize(2);
        assertThat(restored.publicState().tableMelds()).extracting(meld -> meld.meldId())
            .allMatch(id -> id.matches("^[0-9a-f-]{36}$"));
        assertThat(restored.publicState().tableMelds()).extracting(meld -> meld.score())
            .containsExactly(24, 6);
        assertThat(restored.publicState().tableMelds().get(0).gridRow()).isZero();
        assertThat(restored.publicState().tableMelds().get(0).gridColumn()).isZero();
        assertThat(restored.publicState().tableMelds().get(1).gridRow()).isZero();
        assertThat(restored.publicState().tableMelds().get(1).gridColumn()).isEqualTo(13);
        assertThat(restored.publicState().tableMelds())
            .allSatisfy(meld -> {
                assertThat(meld.lastModifiedByUserId()).isEqualTo(requesterId);
                assertThat(meld.lastModifiedBySeatOrder()).isEqualTo(requester.seatOrder());
            });
        assertThat(restored.myRack()).extracting(tile -> tile.tileId()).doesNotContainAnyElementsOf(EXACT_THIRTY);

        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(eventProbe.lastEvent().publicEventType()).isEqualTo("MELDS_COMMITTED");
        assertThat(applicationEvents.stream(RoomEventEnvelope.class)
            .map(envelope -> envelope.event().eventType())
            .filter(eventType -> eventType.equals("ROOM_CLOSED") || eventType.equals("ROOM_REMOVED")))
            .isEmpty();
        MeldsCommittedPayload payload = (MeldsCommittedPayload) eventProbe.lastEvent().publicPayload();
        assertThat(payload.initialMeldScore()).isEqualTo(30);
        assertThat(payload.initialMeldCompleted()).isTrue();
        assertThat(payload.changedMeldIds()).containsExactlyElementsOf(
            restored.publicState().tableMelds().stream().map(meld -> meld.meldId()).toList()
        );
        assertThat(payload.rackContributionCount()).isEqualTo(6);
        assertThat(payload.tableRecomposed()).isFalse();
        GamePrivateState otherState = eventProbe.lastEvent().privateStates().get(next.user().id());
        assertThat(otherState.myRack()).extracting(tile -> tile.tileId()).doesNotContainAnyElementsOf(EXACT_THIRTY);
        assertThat(otherState.publicState().tableMelds()).isEqualTo(restored.publicState().tableMelds());
    }

    @Test
    void beP7F001RackExhaustionFinishesTheGameAwardsTheWinnerAndReleasesTheRoom() {
        StartedGame fixture = startedGame("rack-exhaustion");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterUserId = before.currentTurnUser().id();
        GamePlayer winner = playerRepository.findByGameIdAndUserId(
            fixture.gameId(), requesterUserId
        ).orElseThrow();
        GamePlayer loser = playerRepository.findByGameId(fixture.gameId()).stream()
            .filter(player -> !player.id().equals(winner.id()))
            .findFirst().orElseThrow();
        arrangeRacks(fixture.gameId(), requesterUserId, EXACT_THIRTY);
        retainOnlyRackTiles(fixture.gameId(), winner.id(), EXACT_THIRTY);
        CommitTurnCommand finalCommit = command(before.version(),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6)));

        GameTurnCommandResult result = commitService.commit(
            fixture.gameId(), requesterUserId, finalCommit
        );

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(result.actionType()).isEqualTo(GameTurnCommitService.COMMIT);
        assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(after.terminationReason()).isEqualTo(GameTerminationReason.RACK_EXHAUSTED);
        assertThat(after.winnerUser().id()).isEqualTo(requesterUserId);
        assertThat(after.turnNumber()).isEqualTo(before.turnNumber());
        assertThat(after.currentTurnId()).isEqualTo(before.currentTurnId());
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(rackCount(fixture.gameId(), winner.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT participant_status FROM game_players WHERE id = ?",
            String.class, winner.id()
        )).isEqualTo(GamePlayerStatus.WINNER.name());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT participant_status FROM game_players WHERE id = ?",
            String.class, loser.id()
        )).isEqualTo(GamePlayerStatus.LOSER.name());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM rooms WHERE id = (SELECT room_id FROM games WHERE id = ?)",
            String.class, fixture.gameId()
        )).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM room_players WHERE room_id = (SELECT room_id FROM games WHERE id = ?) AND left_at IS NULL",
            Long.class, fixture.gameId()
        )).isZero();
        assertThat(queryService.activeGame(requesterUserId).active()).isFalse();
        assertThat(queryService.activeGame(loser.user().id()).active()).isFalse();

        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(eventProbe.lastEvent().publicEventType()).isEqualTo("GAME_TERMINATED");
        assertThat(eventProbe.lastEvent().privateStates()).isEmpty();
        GameTerminatedPayload payload = (GameTerminatedPayload) eventProbe.lastEvent().publicPayload();
        assertThat(payload.gameId()).isEqualTo(fixture.gameId());
        assertThat(payload.gameStatus()).isEqualTo(GameStatus.FINISHED.name());
        assertThat(payload.roomStatus()).isEqualTo("CLOSED");
        assertThat(payload.terminationReason()).isEqualTo(GameTerminationReason.RACK_EXHAUSTED.name());
        assertThat(payload.exitedParticipantId()).isNull();
        assertThat(payload.exitedUserId()).isNull();
        assertThat(payload.winnerParticipantId()).isEqualTo(winner.id());
        assertThat(payload.winnerUserId()).isEqualTo(requesterUserId);
        assertThat(applicationEvents.stream(RoomEventEnvelope.class)
            .map(envelope -> envelope.event().eventType())
            .filter(eventType -> eventType.equals("ROOM_CLOSED") || eventType.equals("ROOM_REMOVED")))
            .containsExactly("ROOM_CLOSED", "ROOM_REMOVED");

        List<String> terminalTileState = tileState(fixture.gameId());
        assertBusinessCode(() -> turnCommandService.draw(
            fixture.gameId(), requesterUserId, after.version()
        ), ErrorCode.GAME_NOT_IN_PROGRESS);
        assertBusinessCode(() -> turnCommandService.pass(
            fixture.gameId(), requesterUserId, after.version()
        ), ErrorCode.GAME_NOT_IN_PROGRESS);
        assertBusinessCode(() -> commitService.commit(
            fixture.gameId(), requesterUserId, finalCommit
        ), ErrorCode.GAME_NOT_IN_PROGRESS);
        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(terminalTileState);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
    }

    @Test
    void beP7F002InvalidFinalMeldRollsBackWithoutSelectingAWinner() {
        StartedGame fixture = startedGame("rack-exhaustion-invalid");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterUserId = before.currentTurnUser().id();
        GamePlayer requester = playerRepository.findByGameIdAndUserId(
            fixture.gameId(), requesterUserId
        ).orElseThrow();
        List<String> invalidRun = List.of("RED-07-A", "RED-08-A", "RED-10-A");
        arrangeRacks(fixture.gameId(), requesterUserId, invalidRun);
        retainOnlyRackTiles(fixture.gameId(), requester.id(), invalidRun);
        List<String> beforeTiles = tileState(fixture.gameId());

        assertBusinessCode(() -> commitService.commit(
            fixture.gameId(), requesterUserId,
            command(before.version(), meld(UUID.randomUUID().toString(), invalidRun))
        ), ErrorCode.INVALID_MELD);

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(after.terminationReason()).isNull();
        assertThat(after.winnerUser()).isNull();
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(rackCount(fixture.gameId(), requester.id())).isEqualTo(3);
        assertThat(meldRepository.countByGameId(fixture.gameId())).isZero();
        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(beforeTiles);
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void beP7F003ConcurrentFinalCommitsTransitionAndPublishTerminationExactlyOnce() throws Exception {
        StartedGame fixture = startedGame("rack-exhaustion-race");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterUserId = before.currentTurnUser().id();
        GamePlayer requester = playerRepository.findByGameIdAndUserId(
            fixture.gameId(), requesterUserId
        ).orElseThrow();
        arrangeRacks(fixture.gameId(), requesterUserId, EXACT_THIRTY);
        retainOnlyRackTiles(fixture.gameId(), requester.id(), EXACT_THIRTY);
        CommitTurnCommand finalCommit = command(before.version(),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Object> outcomes;
        try {
            Future<Object> first = executor.submit(() -> outcome(
                () -> commitService.commit(fixture.gameId(), requesterUserId, finalCommit)
            ));
            Future<Object> second = executor.submit(() -> outcome(
                () -> commitService.commit(fixture.gameId(), requesterUserId, finalCommit)
            ));
            outcomes = List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(outcomes.stream().filter(GameTurnCommandResult.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ErrorCode.class::isInstance).map(ErrorCode.class::cast))
            .singleElement().isEqualTo(ErrorCode.GAME_NOT_IN_PROGRESS);
        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(after.terminationReason()).isEqualTo(GameTerminationReason.RACK_EXHAUSTED);
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(rackCount(fixture.gameId(), requester.id())).isZero();
        assertThat(playerRepository.findByGameId(fixture.gameId()))
            .filteredOn(player -> player.participantStatus() == GamePlayerStatus.WINNER)
            .singleElement()
            .extracting(player -> player.user().id()).isEqualTo(requesterUserId);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(eventProbe.lastEvent().publicEventType()).isEqualTo("GAME_TERMINATED");
        assertThat(applicationEvents.stream(RoomEventEnvelope.class)
            .map(envelope -> envelope.event().eventType())
            .filter(eventType -> eventType.equals("ROOM_CLOSED") || eventType.equals("ROOM_REMOVED")))
            .containsExactly("ROOM_CLOSED", "ROOM_REMOVED");
    }

    @Test
    void beP7B001Through003RejectsCollisionAndBoundsBeforePersistingCoordinates() {
        StartedGame fixture = startedGame("grid-layout");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        arrangeRacks(fixture.gameId(), requesterId, EXACT_THIRTY);
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            commandWithLayout(game.version(), List.of(
                new CommitTableMeldCommand(first, EXACT_THIRTY.subList(0, 3), 2, 4),
                new CommitTableMeldCommand(second, EXACT_THIRTY.subList(3, 6), 2, 6)
            ))), ErrorCode.INVALID_TABLE_LAYOUT);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            commandWithLayout(game.version(), List.of(
                new CommitTableMeldCommand(first, EXACT_THIRTY.subList(0, 3), 18, 0),
                new CommitTableMeldCommand(second, EXACT_THIRTY.subList(3, 6), 0, 13)
            ))), ErrorCode.INVALID_TABLE_LAYOUT);
        assertThat(meldRepository.countByGameId(fixture.gameId())).isZero();

        commitService.commit(fixture.gameId(), requesterId, commandWithLayout(game.version(), List.of(
            new CommitTableMeldCommand(first, EXACT_THIRTY.subList(0, 3), 3, 5),
            new CommitTableMeldCommand(second, EXACT_THIRTY.subList(3, 6), 4, 9)
        )));
        var restored = queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds();
        assertThat(restored.get(0).gridRow()).isEqualTo(3);
        assertThat(restored.get(0).gridColumn()).isEqualTo(5);
        assertThat(restored.get(1).gridRow()).isEqualTo(4);
        assertThat(restored.get(1).gridColumn()).isEqualTo(9);
    }

    @Test
    void commit002And025_completedPlayerCanCommitAGroupBelowThirty() {
        StartedGame fixture = startedGame("commit-group");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        List<String> group = List.of("RED-01-A", "BLUE-01-A", "YELLOW-01-A");
        arrangeRacks(fixture.gameId(), requesterId, group);
        jdbcTemplate.update(
            "UPDATE game_players SET initial_meld_completed = TRUE WHERE game_id = ? AND user_id = ?",
            fixture.gameId(), requesterId
        );

        commitService.commit(fixture.gameId(), requesterId, command(game.version(), meld(UUID.randomUUID().toString(), group)));

        var committed = queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds();
        assertThat(committed).singleElement().satisfies(meld -> {
            assertThat(meld.meldType()).isEqualTo("GROUP");
            assertThat(meld.score()).isEqualTo(3);
        });
    }

    @Test
    void commit004And005_lowScoreOrOneInvalidMeldRejectsTheWholeTurn() {
        StartedGame fixture = startedGame("commit-invalid");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        List<String> twentyEight = List.of("RED-07-A", "BLUE-07-A", "YELLOW-07-A", "BLACK-07-A");
        List<String> required = new ArrayList<>(EXACT_THIRTY);
        required.addAll(twentyEight);
        required.add("BLUE-04-A");
        arrangeRacks(fixture.gameId(), requesterId, required);
        List<String> before = tileState(fixture.gameId());

        assertBusinessCode(() -> commitService.commit(
            fixture.gameId(), requesterId,
            command(game.version(), meld(UUID.randomUUID().toString(), twentyEight))
        ), ErrorCode.INITIAL_MELD_SCORE_TOO_LOW);
        assertBusinessCode(() -> commitService.commit(
            fixture.gameId(), requesterId,
            command(game.version(),
                meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
                meld(UUID.randomUUID().toString(), List.of("BLUE-01-A", "BLUE-02-A", "BLUE-04-A")))
        ), ErrorCode.INVALID_MELD);

        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(before);
        assertThat(meldRepository.countByGameId(fixture.gameId())).isZero();
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version());
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void commit006Through011_rejectsDuplicateForeignPoolTableTurnAndVersionInputs() {
        StartedGame fixture = startedGame("commit-ownership");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        arrangeRacks(fixture.gameId(), requesterId, EXACT_THIRTY);
        GamePlayer other = nextPlayer(fixture.gameId(), game.currentTurnSeatOrder());
        String foreignTile = jdbcTemplate.queryForObject(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? ORDER BY position_order FETCH FIRST 1 ROW ONLY",
            String.class, fixture.gameId(), other.id());
        String poolTile = jdbcTemplate.queryForObject(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? AND location = 'POOL' ORDER BY position_order FETCH FIRST 1 ROW ONLY",
            String.class, fixture.gameId());

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(UUID.randomUUID().toString(), List.of("RED-07-A", "RED-08-A", "RED-07-A")))),
            ErrorCode.DUPLICATE_TILE_IN_TURN);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(UUID.randomUUID().toString(), List.of(foreignTile)))),
            ErrorCode.TILE_NOT_IN_RACK);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(UUID.randomUUID().toString(), List.of(poolTile)))),
            ErrorCode.TILE_NOT_IN_RACK);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), other.user().id(),
            command(game.version(), meld(UUID.randomUUID().toString(), List.of(foreignTile)))),
            ErrorCode.NOT_CURRENT_TURN);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version() + 1, meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)))),
            ErrorCode.STALE_GAME_VERSION);

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6))));
        Game committed = gameRepository.findById(fixture.gameId()).orElseThrow();
        GamePlayer original = playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        jdbcTemplate.update(
            "UPDATE games SET current_turn_user_id = ?, current_turn_seat_order = ? WHERE id = ?",
            requesterId, original.seatOrder(), fixture.gameId()
        );
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(committed.version(), meld(UUID.randomUUID().toString(), List.of("RED-07-A")))),
            ErrorCode.INVALID_TABLE_LAYOUT);
    }

    @Test
    void commit013And014_differentCommitOrDrawActionsWithOneVersionCommitOnlyOnce() throws Exception {
        StartedGame fixture = startedGame("commit-race");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        arrangeRacks(fixture.gameId(), requesterId, EXACT_THIRTY);
        CommitTurnCommand command = command(game.version(),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
            meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6)));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> commit = executor.submit(() -> outcome(() -> commitService.commit(fixture.gameId(), requesterId, command)));
            Future<Object> draw = executor.submit(() -> outcome(() -> turnCommandService.draw(fixture.gameId(), requesterId, game.version())));
            List<Object> outcomes = List.of(commit.get(8, TimeUnit.SECONDS), draw.get(8, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(GameTurnCommandResult.class::isInstance).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(ErrorCode.class::isInstance).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version() + 1);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
    }

    @Test
    void commit021And022_beforeCommitFailureRollsBackEverythingAndPublishesNoAfterCommitEvent() {
        StartedGame fixture = startedGame("commit-rollback");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        arrangeRacks(fixture.gameId(), requesterId, EXACT_THIRTY);
        List<String> before = tileState(fixture.gameId());
        eventProbe.failBeforeCommitOnce();

        assertThatThrownBy(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(),
                meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3)),
                meld(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6)))))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("forced commit failure");

        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(before);
        assertThat(meldRepository.countByGameId(fixture.gameId())).isZero();
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version());
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void recompose001Through004_extendsExistingMeldAndRecalculatesServerAuthority() {
        StartedGame fixture = startedGame("recompose-extend");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String meldId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(meldId, "RUN", 6, List.of("RED-01-A", "RED-02-A", "RED-03-A"))),
            List.of("RED-04-A"));

        commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A"))));

        var restored = queryService.privateState(fixture.gameId(), requesterId);
        assertThat(restored.publicState().tableMelds()).singleElement().satisfies(candidate -> {
            assertThat(candidate.meldId()).isEqualTo(meldId);
            assertThat(candidate.meldType()).isEqualTo("RUN");
            assertThat(candidate.score()).isEqualTo(10);
            assertThat(candidate.tiles()).extracting(tile -> tile.tileId())
                .containsExactly("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A");
        });
        MeldsCommittedPayload payload = (MeldsCommittedPayload) eventProbe.lastEvent().publicPayload();
        assertThat(payload.changedMeldIds()).containsExactly(meldId);
        assertThat(payload.rackContributionCount()).isEqualTo(1);
        assertThat(payload.tableRecomposed()).isTrue();
    }

    @Test
    void beP7001And009_currentPlayerExtendsAnotherPlayersMeldAndPreservesCreator() {
        StartedGame fixture = startedGame("cross-player-extend");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        GamePlayer requester = playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        GamePlayer originalCreator = nextPlayer(fixture.gameId(), game.currentTurnSeatOrder());
        String meldId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(meldId, "RUN", 30, List.of("RED-09-A", "RED-10-A", "RED-11-A"))),
            List.of("RED-12-A"));
        jdbcTemplate.update(
            "UPDATE game_melds SET created_by_game_player_id = ?, last_modified_by_game_player_id = ? WHERE game_id = ? AND meld_id = ?",
            originalCreator.id(), originalCreator.id(), fixture.gameId(), meldId
        );
        long creatorRackBefore = rackCount(fixture.gameId(), originalCreator.id());
        long requesterRackBefore = rackCount(fixture.gameId(), requester.id());

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(meldId, List.of("RED-09-A", "RED-10-A", "RED-11-A", "RED-12-A"))));

        var requesterState = queryService.privateState(fixture.gameId(), requesterId);
        var creatorState = queryService.privateState(fixture.gameId(), originalCreator.user().id());
        assertThat(requesterState.publicState().tableMelds()).singleElement().satisfies(candidate -> {
            assertThat(candidate.meldId()).isEqualTo(meldId);
            assertThat(candidate.meldType()).isEqualTo("RUN");
            assertThat(candidate.score()).isEqualTo(42);
            assertThat(candidate.tiles()).extracting(tile -> tile.tileId())
                .containsExactly("RED-09-A", "RED-10-A", "RED-11-A", "RED-12-A");
        });
        assertThat(creatorState.publicState().tableMelds()).isEqualTo(requesterState.publicState().tableMelds());
        assertThat(rackCount(fixture.gameId(), requester.id())).isEqualTo(requesterRackBefore - 1);
        assertThat(rackCount(fixture.gameId(), originalCreator.id())).isEqualTo(creatorRackBefore);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT created_by_game_player_id FROM game_melds WHERE game_id = ? AND meld_id = ?",
            Long.class, fixture.gameId(), meldId)).isEqualTo(originalCreator.id());
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().currentTurnUser().id())
            .isEqualTo(originalCreator.user().id());
    }

    @Test
    void recompose005Through008_splitsOneMeldAndPersistsBothFinalShapes() {
        StartedGame fixture = startedGame("recompose-split");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(first, "RUN", 21,
                List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A", "RED-05-A", "RED-06-A"))),
            List.of("RED-07-A"));

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(first, List.of("RED-01-A", "RED-02-A", "RED-03-A")),
            meld(second, List.of("RED-04-A", "RED-05-A", "RED-06-A", "RED-07-A"))));

        var table = queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds();
        assertThat(table).extracting(candidate -> candidate.meldId()).first().isEqualTo(first);
        assertThat(table).extracting(candidate -> candidate.meldId()).doesNotContain(second);
        assertThat(table).extracting(candidate -> candidate.score()).containsExactly(6, 22);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND location = 'TABLE'",
            Long.class, fixture.gameId())).isEqualTo(7L);
    }

    @Test
    void recompose009Through012_mergesMeldsWithoutUniquePositionCollisionOrOrphan() {
        StartedGame fixture = startedGame("recompose-merge");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String kept = UUID.randomUUID().toString();
        String removed = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true, List.of(
            baseline(kept, "RUN", 6, List.of("RED-01-A", "RED-02-A", "RED-03-A")),
            baseline(removed, "RUN", 15, List.of("RED-04-A", "RED-05-A", "RED-06-A"))
        ), List.of("RED-07-A"));

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(kept, List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A", "RED-05-A", "RED-06-A", "RED-07-A"))));

        assertThat(meldRepository.countByGameId(fixture.gameId())).isEqualTo(1);
        assertThat(meldRepository.existsByGameIdAndMeldId(fixture.gameId(), removed)).isFalse();
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .singleElement().satisfies(candidate -> assertThat(candidate.score()).isEqualTo(28));
    }

    @Test
    void recompose013Through016_reordersMeldsAndMovesBaselineTilesAcrossThem() {
        StartedGame fixture = startedGame("recompose-move");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true, List.of(
            baseline(first, "GROUP", 21, List.of("RED-07-A", "BLUE-07-A", "YELLOW-07-A")),
            baseline(second, "GROUP", 21, List.of("RED-07-B", "BLUE-07-B", "BLACK-07-A"))
        ), List.of("BLACK-07-B"));

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(second, List.of("RED-07-B", "BLUE-07-B", "YELLOW-07-A", "BLACK-07-B")),
            meld(first, List.of("RED-07-A", "BLUE-07-A", "BLACK-07-A"))));

        var table = queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds();
        assertThat(table).extracting(candidate -> candidate.meldId()).containsExactly(second, first);
        assertThat(table.get(0).tiles()).extracting(tile -> tile.tileId())
            .containsExactly("RED-07-B", "BLUE-07-B", "YELLOW-07-A", "BLACK-07-B");
        assertThat(table.get(1).tiles()).extracting(tile -> tile.tileId())
            .containsExactly("RED-07-A", "BLUE-07-A", "BLACK-07-A");
    }

    @Test
    void beP7002Through008_rejectsUnsafeCandidatesRollsBackAndAllowsTheNextValidCommit() {
        StartedGame fixture = startedGame("recompose-reject");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String meldId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(meldId, "RUN", 6, List.of("RED-01-A", "RED-02-A", "RED-03-A"))),
            List.of("RED-04-A", "BLUE-04-A"));
        List<String> before = tileState(fixture.gameId());
        GamePlayer other = nextPlayer(fixture.gameId(), game.currentTurnSeatOrder());
        String foreign = jdbcTemplate.queryForObject(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? FETCH FIRST 1 ROW ONLY",
            String.class, fixture.gameId(), other.id());
        String pool = jdbcTemplate.queryForObject(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? AND location = 'POOL' FETCH FIRST 1 ROW ONLY",
            String.class, fixture.gameId());

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-04-A")))),
            ErrorCode.INVALID_TABLE_LAYOUT);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-03-A")))),
            ErrorCode.DUPLICATE_TILE_IN_TURN);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", foreign)))),
            ErrorCode.TILE_NOT_IN_RACK);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", pool)))),
            ErrorCode.TILE_NOT_IN_RACK);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A")))),
            ErrorCode.TILE_NOT_IN_RACK);
        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", "BLUE-04-A")))),
            ErrorCode.INVALID_MELD);
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version());
        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(before);

        commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A"))));
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version() + 1);
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .singleElement().satisfies(candidate -> assertThat(candidate.score()).isEqualTo(10));
    }

    @Test
    void beP7010_newMeldKeepsTheCurrentCommitPlayerAsCreator() {
        StartedGame fixture = startedGame("new-meld-creator");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        GamePlayer requester = playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        String meldId = UUID.randomUUID().toString();
        List<String> group = List.of("RED-04-A", "BLUE-04-A", "YELLOW-04-A");
        configureCandidateState(fixture.gameId(), requesterId, true, List.of(), group);

        commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(meldId, group)));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT created_by_game_player_id FROM game_melds WHERE game_id = ?",
            Long.class, fixture.gameId())).isEqualTo(requester.id());
    }

    @Test
    void recompose022Through024_initialLockAndInvalidCandidateRollbackPreserveBaseline() {
        StartedGame fixture = startedGame("recompose-initial-lock");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String baselineId = UUID.randomUUID().toString();
        String firstNew = UUID.randomUUID().toString();
        String secondNew = UUID.randomUUID().toString();
        List<String> requiredRack = java.util.stream.Stream.concat(
            EXACT_THIRTY.stream(), java.util.stream.Stream.of("BLACK-04-A")
        ).toList();
        configureCandidateState(fixture.gameId(), requesterId, false,
            List.of(baseline(baselineId, "RUN", 6, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A"))),
            requiredRack);
        List<String> before = tileState(fixture.gameId());

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(baselineId, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A", "BLACK-04-A")),
            meld(firstNew, EXACT_THIRTY.subList(0, 3)),
            meld(secondNew, EXACT_THIRTY.subList(3, 6)))), ErrorCode.INVALID_TABLE_LAYOUT);
        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(before);

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(baselineId, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A")),
            meld(firstNew, EXACT_THIRTY.subList(0, 3)),
            meld(secondNew, EXACT_THIRTY.subList(3, 6))));
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .extracting(candidate -> candidate.meldId()).hasSize(3).startsWith(baselineId)
            .doesNotContain(firstNew, secondNew);
    }

    @Test
    void beP7InitialOrder001_newInitialMeldsCanBePlacedAboveExistingTableMeld() {
        StartedGame fixture = startedGame("initial-order-above");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String baselineId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, false,
            List.of(baseline(baselineId, "RUN", 6, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A"))),
            EXACT_THIRTY);
        jdbcTemplate.update(
            "UPDATE game_melds SET grid_row = 6, grid_column = 8 WHERE game_id = ? AND meld_id = ?",
            fixture.gameId(), baselineId
        );

        commitService.commit(fixture.gameId(), requesterId, commandWithLayout(game.version(), List.of(
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3), 1, 1),
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6), 2, 1),
            new CommitTableMeldCommand(baselineId, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A"), 6, 8)
        )));

        GamePlayer requester = playerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        assertThat(requester.initialMeldCompleted()).isTrue();
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .hasSize(3)
            .filteredOn(meld -> meld.meldId().equals(baselineId))
            .singleElement()
            .satisfies(meld -> {
                assertThat(meld.gridRow()).isEqualTo(6);
                assertThat(meld.gridColumn()).isEqualTo(8);
                assertThat(meld.tiles()).extracting(tile -> tile.tileId())
                    .containsExactly("BLACK-01-A", "BLACK-02-A", "BLACK-03-A");
            });
    }

    @Test
    void beP7InitialOrder002_initialMeldCannotMoveExistingTableMeld() {
        StartedGame fixture = startedGame("initial-lock-position");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String baselineId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, false,
            List.of(baseline(baselineId, "RUN", 6, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A"))),
            EXACT_THIRTY);

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            commandWithLayout(game.version(), List.of(
                new CommitTableMeldCommand(baselineId, List.of("BLACK-01-A", "BLACK-02-A", "BLACK-03-A"), 4, 4),
                new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3), 5, 1),
                new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6), 6, 1)
            ))), ErrorCode.INVALID_TABLE_LAYOUT);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT grid_row FROM game_melds WHERE game_id = ? AND meld_id = ?",
            Integer.class, fixture.gameId(), baselineId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT grid_column FROM game_melds WHERE game_id = ? AND meld_id = ?",
            Integer.class, fixture.gameId(), baselineId)).isZero();
    }

    @Test
    void beP7C001_jokerMeldCanMoveAndExtendWithoutChangingItsRole() {
        StartedGame fixture = startedGame("joker-move");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String meldId = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(meldId, "RUN", 12, List.of("RED-03-A", "JOKER-A", "RED-05-A"))),
            List.of("RED-06-A"));

        commitService.commit(fixture.gameId(), requesterId, commandWithLayout(game.version(), List.of(
            new CommitTableMeldCommand(
                meldId, List.of("RED-03-A", "JOKER-A", "RED-05-A", "RED-06-A"), 4, 7
            )
        )));

        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .singleElement().satisfies(candidate -> {
                assertThat(candidate.gridRow()).isEqualTo(4);
                assertThat(candidate.gridColumn()).isEqualTo(7);
                assertThat(candidate.tiles()).extracting(tile -> tile.tileId())
                    .containsExactly("RED-03-A", "JOKER-A", "RED-05-A", "RED-06-A");
            });
    }

    @Test
    void beP7C002And003_matchingTileReplacesJokerAndRetrievedJokerIsReusedInSameTurn() {
        StartedGame fixture = startedGame("joker-reuse");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String original = UUID.randomUUID().toString();
        String reused = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(original, "RUN", 12, List.of("RED-03-A", "JOKER-A", "RED-05-A"))),
            List.of("RED-04-A", "BLUE-08-A", "BLUE-09-A"));

        commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(original, List.of("RED-03-A", "RED-04-A", "RED-05-A")),
            meld(reused, List.of("JOKER-A", "BLUE-08-A", "BLUE-09-A"))));

        var table = queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds();
        assertThat(table).extracting(candidate -> candidate.meldId()).hasSize(2).startsWith(original)
            .doesNotContain(reused);
        assertThat(table.get(0).tiles()).extracting(tile -> tile.tileId())
            .containsExactly("RED-03-A", "RED-04-A", "RED-05-A");
        assertThat(table.get(1).tiles()).extracting(tile -> tile.tileId())
            .containsExactly("JOKER-A", "BLUE-08-A", "BLUE-09-A");
    }

    @Test
    void beP7C004_tableJokerCannotBeRetrievedIntoRackByOmittingIt() {
        StartedGame fixture = startedGame("joker-rack-reject");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String original = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(original, "RUN", 12, List.of("RED-03-A", "JOKER-A", "RED-05-A"))),
            List.of("RED-04-A"));

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId,
            command(game.version(), meld(original, List.of("RED-03-A", "RED-04-A", "RED-05-A")))),
            ErrorCode.INVALID_TABLE_LAYOUT);
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .singleElement().satisfies(candidate -> assertThat(candidate.tiles()).extracting(tile -> tile.tileId())
                .containsExactly("RED-03-A", "JOKER-A", "RED-05-A"));
    }

    @Test
    void beP7C005And006_wrongJokerReplacementRejectsAndRollsBackTheWholeCandidate() {
        StartedGame fixture = startedGame("joker-invalid-rollback");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = game.currentTurnUser().id();
        String original = UUID.randomUUID().toString();
        configureCandidateState(fixture.gameId(), requesterId, true,
            List.of(baseline(original, "RUN", 12, List.of("RED-03-A", "JOKER-A", "RED-05-A"))),
            List.of(
                "BLUE-08-A", "BLUE-09-A", "RED-01-A", "RED-02-A", "RED-06-A", "RED-07-A",
                "BLUE-04-A", "YELLOW-04-A", "BLACK-04-A"
            ));
        List<String> beforeTiles = tileState(fixture.gameId());

        assertBusinessCode(() -> commitService.commit(fixture.gameId(), requesterId, command(game.version(),
            meld(UUID.randomUUID().toString(), List.of("JOKER-A", "BLUE-08-A", "BLUE-09-A")),
            meld(UUID.randomUUID().toString(), List.of("RED-01-A", "RED-02-A", "RED-03-A")),
            meld(UUID.randomUUID().toString(), List.of("RED-05-A", "RED-06-A", "RED-07-A")),
            meld(UUID.randomUUID().toString(), List.of("BLUE-04-A", "YELLOW-04-A", "BLACK-04-A"))
        )), ErrorCode.INVALID_MELD);

        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version());
        assertThat(tileState(fixture.gameId())).containsExactlyElementsOf(beforeTiles);
        assertThat(eventProbe.afterCommitCount()).isZero();
        assertThat(queryService.privateState(fixture.gameId(), requesterId).publicState().tableMelds())
            .singleElement().satisfies(candidate -> assertThat(candidate.tiles()).extracting(tile -> tile.tileId())
                .containsExactly("RED-03-A", "JOKER-A", "RED-05-A"));
    }

    @Test
    void beP7E001OnlyActuallyChangedMeldReceivesTheCurrentPlayersModifierMetadata() {
        StartedGame fixture = startedGame("meld-last-modifier");
        Game beforeFirstCommit = gameRepository.findById(fixture.gameId()).orElseThrow();
        long firstUserId = beforeFirstCommit.currentTurnUser().id();
        GamePlayer first = playerRepository.findByGameIdAndUserId(fixture.gameId(), firstUserId).orElseThrow();
        arrangeRacks(fixture.gameId(), firstUserId, EXACT_THIRTY);
        commitService.commit(fixture.gameId(), firstUserId, commandWithLayout(beforeFirstCommit.version(), List.of(
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3), 4, 0),
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6), 4, 13)
        )));

        var firstCommittedTable = queryService.privateState(fixture.gameId(), firstUserId).publicState().tableMelds();
        String extendedMeldId = firstCommittedTable.stream()
            .filter(meld -> meld.tiles().stream().map(tile -> tile.tileId()).toList().equals(EXACT_THIRTY.subList(0, 3)))
            .map(meld -> meld.meldId()).findFirst().orElseThrow();
        String untouchedMeldId = firstCommittedTable.stream()
            .filter(meld -> meld.tiles().stream().map(tile -> tile.tileId()).toList().equals(EXACT_THIRTY.subList(3, 6)))
            .map(meld -> meld.meldId()).findFirst().orElseThrow();

        Game beforeSecondCommit = gameRepository.findById(fixture.gameId()).orElseThrow();
        long secondUserId = beforeSecondCommit.currentTurnUser().id();
        GamePlayer second = playerRepository.findByGameIdAndUserId(fixture.gameId(), secondUserId).orElseThrow();
        jdbcTemplate.update("UPDATE game_players SET initial_meld_completed = TRUE WHERE id = ?", second.id());
        moveTileToRackPreservingTable(fixture.gameId(), second.id(), "RED-10-A");

        commitService.commit(fixture.gameId(), secondUserId, commandWithLayout(beforeSecondCommit.version(), List.of(
            new CommitTableMeldCommand(extendedMeldId, List.of("RED-07-A", "RED-08-A", "RED-09-A", "RED-10-A"), 4, 0),
            new CommitTableMeldCommand(untouchedMeldId, EXACT_THIRTY.subList(3, 6), 4, 13)
        )));

        var table = queryService.privateState(fixture.gameId(), secondUserId).publicState().tableMelds();
        var extended = table.stream().filter(meld -> meld.meldId().equals(extendedMeldId)).findFirst().orElseThrow();
        var untouched = table.stream().filter(meld -> meld.meldId().equals(untouchedMeldId)).findFirst().orElseThrow();
        assertThat(extended.lastModifiedByUserId()).isEqualTo(secondUserId);
        assertThat(extended.lastModifiedBySeatOrder()).isEqualTo(second.seatOrder());
        assertThat(untouched.lastModifiedByUserId()).isEqualTo(firstUserId);
        assertThat(untouched.lastModifiedBySeatOrder()).isEqualTo(first.seatOrder());
    }

    @Test
    void beP7E003NewInitialMeldAboveExistingTableDoesNotRecolorUntouchedMelds() {
        StartedGame fixture = startedGame("meld-order-only-modifier");
        Game beforeFirstCommit = gameRepository.findById(fixture.gameId()).orElseThrow();
        long firstUserId = beforeFirstCommit.currentTurnUser().id();
        GamePlayer first = playerRepository.findByGameIdAndUserId(fixture.gameId(), firstUserId).orElseThrow();
        arrangeRacks(fixture.gameId(), firstUserId, EXACT_THIRTY);
        commitService.commit(fixture.gameId(), firstUserId, commandWithLayout(beforeFirstCommit.version(), List.of(
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(0, 3), 4, 0),
            new CommitTableMeldCommand(UUID.randomUUID().toString(), EXACT_THIRTY.subList(3, 6), 4, 13)
        )));

        var firstCommittedTable = queryService.privateState(fixture.gameId(), firstUserId).publicState().tableMelds();
        String firstExistingId = firstCommittedTable.stream()
            .filter(meld -> meld.tiles().stream().map(tile -> tile.tileId()).toList().equals(EXACT_THIRTY.subList(0, 3)))
            .map(meld -> meld.meldId()).findFirst().orElseThrow();
        String secondExistingId = firstCommittedTable.stream()
            .filter(meld -> meld.tiles().stream().map(tile -> tile.tileId()).toList().equals(EXACT_THIRTY.subList(3, 6)))
            .map(meld -> meld.meldId()).findFirst().orElseThrow();

        Game beforeSecondCommit = gameRepository.findById(fixture.gameId()).orElseThrow();
        long secondUserId = beforeSecondCommit.currentTurnUser().id();
        GamePlayer second = playerRepository.findByGameIdAndUserId(fixture.gameId(), secondUserId).orElseThrow();
        List<String> secondInitialTiles = List.of(
            "YELLOW-07-A", "YELLOW-08-A", "YELLOW-09-A",
            "BLACK-01-A", "BLACK-02-A", "BLACK-03-A"
        );
        secondInitialTiles.forEach(tileId -> moveTileToRackPreservingTable(fixture.gameId(), second.id(), tileId));

        String firstNewId = UUID.randomUUID().toString();
        String secondNewId = UUID.randomUUID().toString();
        commitService.commit(fixture.gameId(), secondUserId, commandWithLayout(beforeSecondCommit.version(), List.of(
            new CommitTableMeldCommand(firstNewId, secondInitialTiles.subList(0, 3), 0, 0),
            new CommitTableMeldCommand(secondNewId, secondInitialTiles.subList(3, 6), 0, 13),
            new CommitTableMeldCommand(firstExistingId, EXACT_THIRTY.subList(0, 3), 4, 0),
            new CommitTableMeldCommand(secondExistingId, EXACT_THIRTY.subList(3, 6), 4, 13)
        )));

        var table = queryService.privateState(fixture.gameId(), secondUserId).publicState().tableMelds();
        assertThat(table.stream()
            .filter(meld -> meld.meldId().equals(firstExistingId) || meld.meldId().equals(secondExistingId))
            .toList())
            .allSatisfy(meld -> {
                assertThat(meld.lastModifiedByUserId()).isEqualTo(firstUserId);
                assertThat(meld.lastModifiedBySeatOrder()).isEqualTo(first.seatOrder());
            });
        assertThat(table.stream()
            .filter(meld -> !meld.meldId().equals(firstExistingId) && !meld.meldId().equals(secondExistingId))
            .toList())
            .hasSize(2)
            .allSatisfy(meld -> {
                assertThat(meld.lastModifiedByUserId()).isEqualTo(secondUserId);
                assertThat(meld.lastModifiedBySeatOrder()).isEqualTo(second.seatOrder());
            });
    }

    private void moveTileToRackPreservingTable(long gameId, long targetPlayerId, String tileId) {
        Integer nextPosition = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(position_order), -1) + 1 FROM game_tiles WHERE game_id = ? AND location = 'RACK' AND owner_game_player_id = ?",
            Integer.class, gameId, targetPlayerId
        );
        jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
            targetPlayerId, nextPosition, gameId, tileId
        );
    }

    private StartedGame startedGame(String prefix) {
        User owner = user(prefix + "-owner@example.com", prefix + "Owner");
        User second = user(prefix + "-second@example.com", prefix + "Second");
        long roomId = roomCommandService.create(owner.id(), "커밋검증방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);
        GameStartResult result = gameStartService.startGame(roomId, owner.id());
        eventProbe.reset();
        return new StartedGame(result.gameId());
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now));
    }

    private void arrangeRacks(long gameId, long requesterUserId, List<String> requiredRequesterTiles) {
        List<GamePlayer> players = playerRepository.findByGameId(gameId).stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder)).toList();
        GamePlayer requester = players.stream().filter(player -> player.user().id() == requesterUserId).findFirst().orElseThrow();
        GamePlayer other = players.stream().filter(player -> player.id() != requester.id()).findFirst().orElseThrow();
        List<String> all = jdbcTemplate.queryForList(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? ORDER BY tile_id", String.class, gameId);
        LinkedHashSet<String> requesterRack = new LinkedHashSet<>(requiredRequesterTiles);
        all.stream().filter(tileId -> !requesterRack.contains(tileId)).limit(14 - requesterRack.size()).forEach(requesterRack::add);
        LinkedHashSet<String> otherRack = new LinkedHashSet<>();
        all.stream().filter(tileId -> !requesterRack.contains(tileId)).limit(14).forEach(otherRack::add);
        int poolPosition = 0;
        int requesterPosition = 0;
        int otherPosition = 0;
        for (String tileId : all) {
            if (requesterRack.contains(tileId)) {
                jdbcTemplate.update("UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                    requester.id(), requesterPosition++, gameId, tileId);
            } else if (otherRack.contains(tileId)) {
                jdbcTemplate.update("UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                    other.id(), otherPosition++, gameId, tileId);
            } else {
                jdbcTemplate.update("UPDATE game_tiles SET location = 'POOL', owner_game_player_id = NULL, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                    poolPosition++, gameId, tileId);
            }
        }
    }

    private void retainOnlyRackTiles(long gameId, long gamePlayerId, List<String> retainedTileIds) {
        List<String> rackTileIds = jdbcTemplate.queryForList(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? AND location = 'RACK' ORDER BY position_order",
            String.class, gameId, gamePlayerId
        );
        Integer nextPoolPosition = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(position_order), -1) + 1 FROM game_tiles WHERE game_id = ? AND location = 'POOL'",
            Integer.class, gameId
        );
        int poolPosition = nextPoolPosition == null ? 0 : nextPoolPosition;
        for (String tileId : rackTileIds) {
            if (retainedTileIds.contains(tileId)) continue;
            jdbcTemplate.update(
                "UPDATE game_tiles SET location = 'POOL', owner_game_player_id = NULL, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                poolPosition++, gameId, tileId
            );
        }
    }

    private void configureCandidateState(long gameId, long requesterUserId, boolean initialCompleted,
                                         List<BaselineMeld> tableMelds,
                                         List<String> requiredRequesterTiles) {
        List<GamePlayer> players = playerRepository.findByGameId(gameId).stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder)).toList();
        GamePlayer requester = players.stream().filter(player -> player.user().id() == requesterUserId)
            .findFirst().orElseThrow();
        GamePlayer other = players.stream().filter(player -> !player.id().equals(requester.id()))
            .findFirst().orElseThrow();
        List<String> all = jdbcTemplate.queryForList(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? ORDER BY tile_id", String.class, gameId);
        LinkedHashSet<String> tableIds = tableMelds.stream().flatMap(meld -> meld.tileIds().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> requesterRack = new LinkedHashSet<>(requiredRequesterTiles);
        all.stream().filter(id -> !tableIds.contains(id) && !requesterRack.contains(id))
            .limit(14 - requesterRack.size()).forEach(requesterRack::add);
        LinkedHashSet<String> otherRack = new LinkedHashSet<>();
        all.stream().filter(id -> !tableIds.contains(id) && !requesterRack.contains(id))
            .limit(14).forEach(otherRack::add);
        int poolPosition = 0;
        for (String tileId : all) {
            jdbcTemplate.update(
                "UPDATE game_tiles SET location = 'POOL', owner_game_player_id = NULL, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                poolPosition++, gameId, tileId);
        }
        for (int position = 0; position < tableMelds.size(); position++) {
            BaselineMeld meld = tableMelds.get(position);
            jdbcTemplate.update(
                "INSERT INTO game_melds(game_id, meld_id, position_order, grid_row, grid_column, meld_type, score, created_by_game_player_id, last_modified_by_game_player_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                gameId, meld.meldId(), position, position / 2, (position % 2) * 13,
                meld.meldType(), meld.score(), requester.id(), requester.id());
            Long persistedMeldId = jdbcTemplate.queryForObject(
                "SELECT id FROM game_melds WHERE game_id = ? AND meld_id = ?", Long.class, gameId, meld.meldId());
            for (int tilePosition = 0; tilePosition < meld.tileIds().size(); tilePosition++) {
                jdbcTemplate.update(
                    "UPDATE game_tiles SET location = 'TABLE', owner_game_player_id = NULL, game_meld_id = ?, position_order = ? WHERE game_id = ? AND tile_id = ?",
                    persistedMeldId, tilePosition, gameId, meld.tileIds().get(tilePosition));
            }
        }
        int requesterPosition = 0;
        for (String tileId : requesterRack) {
            jdbcTemplate.update(
                "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                requester.id(), requesterPosition++, gameId, tileId);
        }
        int otherPosition = 0;
        for (String tileId : otherRack) {
            jdbcTemplate.update(
                "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, game_meld_id = NULL, position_order = ? WHERE game_id = ? AND tile_id = ?",
                other.id(), otherPosition++, gameId, tileId);
        }
        jdbcTemplate.update(
            "UPDATE game_players SET initial_meld_completed = ? WHERE id = ?", initialCompleted, requester.id());
    }

    private GamePlayer nextPlayer(long gameId, int currentSeatOrder) {
        List<GamePlayer> players = playerRepository.findByGameId(gameId).stream()
            .sorted(java.util.Comparator.comparingInt(GamePlayer::seatOrder)).toList();
        return players.stream().filter(player -> player.seatOrder() > currentSeatOrder).findFirst().orElse(players.get(0));
    }

    private List<String> tileState(long gameId) {
        return jdbcTemplate.query(
            "SELECT tile_id, location, owner_game_player_id, game_meld_id, position_order FROM game_tiles WHERE game_id = ? ORDER BY tile_id",
            (rs, row) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getObject(3) + "|" + rs.getObject(4) + "|" + rs.getInt(5),
            gameId
        );
    }

    private long rackCount(long gameId, long gamePlayerId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? AND location = 'RACK'",
            Long.class, gameId, gamePlayerId
        );
    }

    private static CommitTableMeldCommand meld(String meldId, List<String> tileIds) {
        return new CommitTableMeldCommand(meldId, tileIds);
    }

    private static BaselineMeld baseline(String meldId, String meldType, int score, List<String> tileIds) {
        return new BaselineMeld(meldId, meldType, score, tileIds);
    }

    private static CommitTurnCommand command(long version, CommitTableMeldCommand... melds) {
        List<CommitTilePlacementCommand> placements = new ArrayList<>();
        for (int index = 0; index < melds.length; index++) {
            CommitTableMeldCommand meld = melds[index];
            int gridRow = index / 2;
            int gridColumn = (index % 2) * 13;
            for (int position = 0; position < meld.tileIds().size(); position++) {
                placements.add(new CommitTilePlacementCommand(
                    meld.tileIds().get(position), gridRow, gridColumn + position
                ));
            }
        }
        return new CommitTurnCommand(UUID.randomUUID().toString(), version, placements, null);
    }

    private static CommitTurnCommand commandWithLayout(long version, List<CommitTableMeldCommand> melds) {
        List<CommitTilePlacementCommand> placements = melds.stream().flatMap(meld ->
            java.util.stream.IntStream.range(0, meld.tileIds().size()).mapToObj(position ->
                new CommitTilePlacementCommand(
                    meld.tileIds().get(position), meld.gridRow(), meld.gridColumn() + position
                )
            )
        ).toList();
        return new CommitTurnCommand(UUID.randomUUID().toString(), version, placements, null);
    }

    private static void assertBusinessCode(ThrowingRunnable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::run).isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(expected);
    }

    private static Object outcome(ThrowingSupplier supplier) {
        try { return supplier.get(); }
        catch (BusinessException exception) { return exception.errorCode(); }
    }

    private record StartedGame(long gameId) {}
    private record BaselineMeld(String meldId, String meldType, int score, List<String> tileIds) {}

    @FunctionalInterface interface ThrowingRunnable { void run(); }
    @FunctionalInterface interface ThrowingSupplier { Object get(); }

    @TestConfiguration
    static class CommitEventProbeConfiguration {
        @Bean CommitEventProbe commitEventProbe() { return new CommitEventProbe(); }
    }

    static final class CommitEventProbe {
        private final AtomicBoolean failBeforeCommit = new AtomicBoolean();
        private final AtomicInteger afterCommit = new AtomicInteger();
        private final AtomicReference<GameTurnCommittedEvent> lastEvent = new AtomicReference<>();
        void reset() { failBeforeCommit.set(false); afterCommit.set(0); lastEvent.set(null); }
        void failBeforeCommitOnce() { failBeforeCommit.set(true); }
        int afterCommitCount() { return afterCommit.get(); }
        GameTurnCommittedEvent lastEvent() { return lastEvent.get(); }
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void before(GameTurnCommittedEvent event) {
            if (failBeforeCommit.compareAndSet(true, false)) throw new IllegalStateException("forced commit failure");
        }
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void after(GameTurnCommittedEvent event) { lastEvent.set(event); afterCommit.incrementAndGet(); }
    }
}
