package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GameStartConcurrencyIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void concurrentStartsWithDifferentActionsCreateExactlyOneGame() throws Exception {
        User owner = user("concurrent-owner@example.com", "concurrentOwner");
        User second = user("concurrent-second@example.com", "concurrentSecond");
        long roomId = roomCommandService.create(owner.id(), "동시시작방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<StartOutcome>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                    try {
                        return StartOutcome.success(gameStartService.startGame(roomId, owner.id()));
                    } catch (BusinessException exception) {
                        return StartOutcome.rejected(exception.errorCode());
                    }
                }));
            }
            start.countDown();

            List<StartOutcome> outcomes = futures.stream()
                .map(future -> get(future))
                .toList();

            assertThat(outcomes).filteredOn(outcome -> outcome.success()).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.success())
                .extracting(StartOutcome::errorCode)
                .containsExactly(ErrorCode.ROOM_ALREADY_PLAYING);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_players", Integer.class)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_tiles", Integer.class)).isEqualTo(106);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT tile_id) FROM game_tiles", Integer.class
            )).isEqualTo(106);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rooms WHERE id = ? AND status = 'PLAYING'", Integer.class, roomId
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static StartOutcome get(Future<StartOutcome> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private record StartOutcome(boolean success, GameStartResult result, ErrorCode errorCode) {
        static StartOutcome success(GameStartResult result) {
            return new StartOutcome(true, result, null);
        }

        static StartOutcome rejected(ErrorCode errorCode) {
            return new StartOutcome(false, null, errorCode);
        }
    }
}
