package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GameRackTileView;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.TileDrawnPayload;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.room.application.RoomCommandService;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@SpringBootTest
@ActiveProfiles("test")
@Import(GameTurnCommandIntegrationTest.EventProbeConfiguration.class)
class GameTurnCommandIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired GameTurnCommandService commandService;
    @Autowired GameQueryService gameQueryService;
    @Autowired GameRepository gameRepository;
    @Autowired GamePlayerRepository gamePlayerRepository;
    @Autowired GameTileRepository gameTileRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;
    @Autowired EventProbe eventProbe;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
        eventProbe.reset();
    }

    @Test
    void currentPlayerDrawsTheFirstPoolTileToTheRackEndAndAdvancesTheTurn() {
        StartedGame fixture = startedGame("draw");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        GamePlayer requester = gamePlayerRepository.findByGameIdAndUserId(fixture.gameId(), requesterId).orElseThrow();
        GamePlayer expectedNext = nextPlayer(fixture.gameId(), before.currentTurnSeatOrder());
        Map<String, Object> firstPool = firstPoolTile(fixture.gameId());
        int previousRackMax = jdbcTemplate.queryForObject(
            "SELECT MAX(position_order) FROM game_tiles WHERE owner_game_player_id = ? AND location = 'RACK'",
            Integer.class,
            requester.id()
        );
        List<String> untouchedBefore = tileStateExcept(fixture.gameId(), (String) firstPool.get("tile_id"));
        String previousTurnId = before.currentTurnId();
        LocalDateTime previousDeadline = before.currentTurnDeadlineAt();
        long previousVersion = before.version();
        int previousTurnNumber = before.turnNumber();

        GameTurnCommandResult result = commandService.draw(fixture.gameId(), requesterId, previousVersion);

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        Map<String, Object> drawnAfter = jdbcTemplate.queryForMap(
            "SELECT location, owner_game_player_id, position_order FROM game_tiles WHERE game_id = ? AND tile_id = ?",
            fixture.gameId(),
            firstPool.get("tile_id")
        );
        assertThat(result.actionType()).isEqualTo("DRAW");
        assertThat(result.gameVersion()).isEqualTo(previousVersion + 1);
        assertThat(after.version()).isEqualTo(previousVersion + 1);
        assertThat(after.turnNumber()).isEqualTo(previousTurnNumber + 1);
        assertThat(after.currentTurnUser().id()).isEqualTo(expectedNext.user().id());
        assertThat(after.currentTurnSeatOrder()).isEqualTo(expectedNext.seatOrder());
        assertThat(after.currentTurnId()).isNotEqualTo(previousTurnId);
        assertThat(after.currentTurnDeadlineAt()).isAfter(previousDeadline);
        assertThat(after.consecutivePassCount()).isZero();
        assertThat(gameTileRepository.countByGameIdAndLocation(fixture.gameId(), GameTileLocation.POOL)).isEqualTo(77);
        assertThat(drawnAfter.get("location")).isEqualTo("RACK");
        assertThat(((Number) drawnAfter.get("owner_game_player_id")).longValue()).isEqualTo(requester.id());
        assertThat(((Number) drawnAfter.get("position_order")).intValue()).isEqualTo(previousRackMax + 1);
        assertThat(tileStateExcept(fixture.gameId(), (String) firstPool.get("tile_id")))
            .containsExactlyElementsOf(untouchedBefore);

        GameTurnCommittedEvent event = eventProbe.lastEvent();
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(event.publicEventType()).isEqualTo("TILE_DRAWN");
        assertThat(event.publicPayload()).isInstanceOf(TileDrawnPayload.class);
        TileDrawnPayload payload = (TileDrawnPayload) event.publicPayload();
        assertThat(payload.gameVersion()).isEqualTo(after.version());
        assertThat(payload.drawnByUserId()).isEqualTo(requesterId);
        assertThat(payload.drawnByRackCount()).isEqualTo(15);
        assertThat(payload.tilePoolCount()).isEqualTo(77);
        assertThat(TileDrawnPayload.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("tileId", "color", "number", "joker", "poolOrder");

        GamePrivateState requesterState = event.privateStates().get(requesterId);
        GamePrivateState otherState = event.privateStates().get(expectedNext.user().id());
        String drawnTileId = (String) firstPool.get("tile_id");
        assertThat(requesterState.myRack()).hasSize(15)
            .extracting(GameRackTileView::tileId).contains(drawnTileId);
        assertThat(otherState.myRack()).hasSize(14)
            .extracting(GameRackTileView::tileId).doesNotContain(drawnTileId);
        assertThat(requesterState.publicState().players())
            .filteredOn(player -> player.userId() == requesterId)
            .singleElement().extracting(player -> player.rackTileCount()).isEqualTo(15);

        GamePrivateState restoredOther = gameQueryService.privateState(fixture.gameId(), expectedNext.user().id());
        assertThat(restoredOther.myRack()).extracting(GameRackTileView::tileId).doesNotContain(drawnTileId);
        assertThat(restoredOther.publicState().gameVersion()).isEqualTo(after.version());
        assertThat(restoredOther.publicState().currentTurnId()).isEqualTo(after.currentTurnId());
    }

    @Test
    void staleVersionIsRejectedBeforeTheCurrentTurnCheck() {
        StartedGame fixture = startedGame("stale");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();
        long nonCurrentUserId = fixture.users().stream()
            .map(User::id)
            .filter(userId -> userId != game.currentTurnUser().id())
            .findFirst()
            .orElseThrow();

        assertBusinessCode(
            () -> commandService.draw(fixture.gameId(), nonCurrentUserId, game.version() + 1),
            ErrorCode.STALE_GAME_VERSION
        );
        assertBusinessCode(
            () -> commandService.draw(fixture.gameId(), nonCurrentUserId, game.version()),
            ErrorCode.NOT_CURRENT_TURN
        );
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void passIsRejectedWhileThePoolStillContainsTiles() {
        StartedGame fixture = startedGame("pass-reject");
        Game game = gameRepository.findById(fixture.gameId()).orElseThrow();

        assertBusinessCode(
            () -> commandService.pass(fixture.gameId(), game.currentTurnUser().id(), game.version()),
            ErrorCode.PASS_NOT_ALLOWED
        );
        assertThat(gameRepository.findById(fixture.gameId()).orElseThrow().version()).isEqualTo(game.version());
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void emptyPoolAllowsPassAndDrawAfterwardResetsThePassCount() {
        StartedGame fixture = startedGame("pass");
        Game beforePass = gameRepository.findById(fixture.gameId()).orElseThrow();
        moveEntirePoolToRack(fixture.gameId(), beforePass.currentTurnUser().id());
        GamePlayer expectedNext = nextPlayer(fixture.gameId(), beforePass.currentTurnSeatOrder());

        commandService.pass(fixture.gameId(), beforePass.currentTurnUser().id(), beforePass.version());

        Game afterPass = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(afterPass.version()).isEqualTo(beforePass.version() + 1);
        assertThat(afterPass.currentTurnUser().id()).isEqualTo(expectedNext.user().id());
        assertThat(afterPass.turnNumber()).isEqualTo(beforePass.turnNumber() + 1);
        assertThat(afterPass.consecutivePassCount()).isEqualTo(1);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(eventProbe.lastEvent().publicEventType()).isEqualTo("TURN_PASSED");

        moveOneRackTileBackToPool(fixture.gameId(), beforePass.currentTurnUser().id());
        commandService.draw(fixture.gameId(), afterPass.currentTurnUser().id(), afterPass.version());

        Game afterDraw = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(afterDraw.consecutivePassCount()).isZero();
        assertThat(afterDraw.version()).isEqualTo(afterPass.version() + 1);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(2);
    }

    @Test
    void emptyPoolRejectsDrawWithoutChangingState() {
        StartedGame fixture = startedGame("draw-empty");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        moveEntirePoolToRack(fixture.gameId(), before.currentTurnUser().id());

        assertBusinessCode(
            () -> commandService.draw(fixture.gameId(), before.currentTurnUser().id(), before.version()),
            ErrorCode.DRAW_POOL_EMPTY
        );

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.turnNumber()).isEqualTo(before.turnNumber());
        assertThat(after.currentTurnId()).isEqualTo(before.currentTurnId());
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    @Test
    void differentActionsWithTheSameVersionCommitOnlyOnceAndRejectTheOther() throws Exception {
        StartedGame fixture = startedGame("concurrent");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> invokeDraw(fixture.gameId(), requesterId, before.version()));
            Future<Object> second = executor.submit(() -> invokeDraw(fixture.gameId(), requesterId, before.version()));

            List<Object> outcomes = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            long successCount = outcomes.stream().filter(GameTurnCommandResult.class::isInstance).count();
            List<ErrorCode> rejectionCodes = outcomes.stream()
                .filter(ErrorCode.class::isInstance)
                .map(ErrorCode.class::cast)
                .toList();

            assertThat(successCount).isEqualTo(1);
            assertThat(rejectionCodes)
                .hasSize(1)
                .allSatisfy(errorCode -> assertThat(errorCode)
                    .isIn(ErrorCode.STALE_GAME_VERSION, ErrorCode.NOT_CURRENT_TURN));
            assertThat(successCount + rejectionCodes.size()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(gameTileRepository.countByGameIdAndLocation(fixture.gameId(), GameTileLocation.POOL)).isEqualTo(77);
        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
    }

    @Test
    void beforeCommitFailureRollsBackTileTurnVersionAndAfterCommitEvent() {
        StartedGame fixture = startedGame("rollback");
        Game before = gameRepository.findById(fixture.gameId()).orElseThrow();
        long requesterId = before.currentTurnUser().id();
        long poolCount = gameTileRepository.countByGameIdAndLocation(fixture.gameId(), GameTileLocation.POOL);
        long rackCount = rackCount(fixture.gameId(), requesterId);
        List<String> tileState = tileStateExcept(fixture.gameId(), "__none__");
        eventProbe.failBeforeCommitOnce();

        assertThatThrownBy(() -> commandService.draw(fixture.gameId(), requesterId, before.version()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("forced game-turn before-commit failure");

        Game after = gameRepository.findById(fixture.gameId()).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.currentTurnUser().id()).isEqualTo(before.currentTurnUser().id());
        assertThat(after.currentTurnSeatOrder()).isEqualTo(before.currentTurnSeatOrder());
        assertThat(after.turnNumber()).isEqualTo(before.turnNumber());
        assertThat(after.currentTurnId()).isEqualTo(before.currentTurnId());
        assertThat(gameTileRepository.countByGameIdAndLocation(fixture.gameId(), GameTileLocation.POOL))
            .isEqualTo(poolCount);
        assertThat(rackCount(fixture.gameId(), requesterId)).isEqualTo(rackCount);
        assertThat(tileStateExcept(fixture.gameId(), "__none__")).containsExactlyElementsOf(tileState);
        assertThat(eventProbe.afterCommitCount()).isZero();
    }

    private StartedGame startedGame(String prefix) {
        User owner = user(prefix + "-owner@example.com", prefix + "Owner");
        User second = user(prefix + "-second@example.com", prefix + "Second");
        long roomId = roomCommandService.create(owner.id(), "턴검증방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);
        GameStartResult result = gameStartService.startGame(roomId, owner.id());
        eventProbe.reset();
        return new StartedGame(result.gameId(), List.of(owner, second));
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private GamePlayer nextPlayer(long gameId, int currentSeatOrder) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        return players.stream().filter(player -> player.seatOrder() > currentSeatOrder).findFirst().orElse(players.get(0));
    }

    private Map<String, Object> firstPoolTile(long gameId) {
        return jdbcTemplate.queryForMap(
            "SELECT tile_id, position_order FROM game_tiles WHERE game_id = ? AND location = 'POOL' "
                + "ORDER BY position_order FETCH FIRST 1 ROW ONLY",
            gameId
        );
    }

    private List<String> tileStateExcept(long gameId, String excludedTileId) {
        return jdbcTemplate.query(
            "SELECT tile_id, location, owner_game_player_id, position_order FROM game_tiles "
                + "WHERE game_id = ? AND tile_id <> ? ORDER BY tile_id",
            (rs, rowNum) -> rs.getString("tile_id") + "|" + rs.getString("location") + "|"
                + rs.getObject("owner_game_player_id") + "|" + rs.getInt("position_order"),
            gameId,
            excludedTileId
        );
    }

    private void moveEntirePoolToRack(long gameId, long userId) {
        Long playerId = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseThrow().id();
        jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'RACK', owner_game_player_id = ?, "
                + "position_order = position_order + 14 WHERE game_id = ? AND location = 'POOL'",
            playerId,
            gameId
        );
    }

    private void moveOneRackTileBackToPool(long gameId, long userId) {
        Long playerId = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseThrow().id();
        Long tileRowId = jdbcTemplate.queryForObject(
            "SELECT id FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? "
                + "ORDER BY position_order DESC FETCH FIRST 1 ROW ONLY",
            Long.class,
            gameId,
            playerId
        );
        jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'POOL', owner_game_player_id = NULL, position_order = 0 WHERE id = ?",
            tileRowId
        );
    }

    private long rackCount(long gameId, long userId) {
        Long playerId = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseThrow().id();
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ? AND owner_game_player_id = ? AND location = 'RACK'",
            Long.class,
            gameId,
            playerId
        );
    }

    private Object invokeDraw(long gameId, long requesterId, long version) {
        try {
            return commandService.draw(gameId, requesterId, version);
        } catch (BusinessException exception) {
            return exception.errorCode();
        }
    }

    private static void assertBusinessCode(ThrowingRunnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(expected);
    }

    private record StartedGame(long gameId, List<User> users) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @TestConfiguration
    static class EventProbeConfiguration {
        @Bean
        EventProbe gameTurnEventProbe() {
            return new EventProbe();
        }
    }

    static final class EventProbe {
        private final AtomicBoolean failBeforeCommit = new AtomicBoolean();
        private final AtomicInteger afterCommit = new AtomicInteger();
        private final AtomicReference<GameTurnCommittedEvent> lastEvent = new AtomicReference<>();

        void reset() {
            failBeforeCommit.set(false);
            afterCommit.set(0);
            lastEvent.set(null);
        }

        void failBeforeCommitOnce() {
            failBeforeCommit.set(true);
        }

        int afterCommitCount() {
            return afterCommit.get();
        }

        GameTurnCommittedEvent lastEvent() {
            return lastEvent.get();
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void beforeCommit(GameTurnCommittedEvent event) {
            if (failBeforeCommit.compareAndSet(true, false)) {
                throw new IllegalStateException("forced game-turn before-commit failure");
            }
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void afterCommit(GameTurnCommittedEvent event) {
            lastEvent.set(event);
            afterCommit.incrementAndGet();
        }
    }
}
