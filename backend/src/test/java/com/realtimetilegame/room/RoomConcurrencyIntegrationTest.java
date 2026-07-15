package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RoomConcurrencyIntegrationTest {
    @Autowired RoomCommandService commandService;
    @Autowired UserRepository userRepository;
    @Autowired RoomPlayerRepository playerRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() { executor.shutdownNow(); }

    @Test
    void concurrentRoomCreationBySameUserCreatesOnlyOneActiveMembership() throws Exception {
        User user = user("same@example.com", "sameUser");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = List.of(
            executor.submit(task(start, () -> commandService.create(user.id(), "첫번째방", 4, "CLASSIC", 120, true))),
            executor.submit(task(start, () -> commandService.create(user.id(), "두번째방", 4, "CLASSIC", 120, true)))
        );
        start.countDown();

        List<Result> results = futures.stream().map(RoomConcurrencyIntegrationTest::await).toList();
        assertThat(results.stream().filter(Result::success).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.errorCode() == ErrorCode.USER_ALREADY_IN_ROOM).count()).isEqualTo(1);
        assertThat(playerRepository.findActiveByUserId(user.id())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms WHERE status='WAITING'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentJoinForLastSeatAllowsExactlyOneUser() throws Exception {
        User owner = user("owner@example.com", "owner");
        User first = user("first@example.com", "first");
        User second = user("second@example.com", "second");
        long roomId = commandService.create(owner.id(), "두명방", 2, "CLASSIC", 120, true).roomId();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = List.of(
            executor.submit(task(start, () -> commandService.join(roomId, first.id()))),
            executor.submit(task(start, () -> commandService.join(roomId, second.id())))
        );
        start.countDown();

        List<Result> results = futures.stream().map(RoomConcurrencyIntegrationTest::await).toList();
        assertThat(results.stream().filter(Result::success).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.errorCode() == ErrorCode.ROOM_FULL).count()).isEqualTo(1);
        assertThat(playerRepository.countActiveByRoomId(roomId)).isEqualTo(2);
    }

    @Test
    void concurrentCreateAndJoinBySameUserLeavesOneActiveMembership() throws Exception {
        User owner = user("target-owner@example.com", "targetOwner");
        User contender = user("contender@example.com", "contender");
        long targetRoomId = commandService.create(owner.id(), "입장대상", 4, "CLASSIC", 120, true).roomId();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = List.of(
            executor.submit(task(start, () -> commandService.create(contender.id(), "동시생성", 4, "CLASSIC", 120, true))),
            executor.submit(task(start, () -> commandService.join(targetRoomId, contender.id())))
        );
        start.countDown();

        List<Result> results = futures.stream().map(RoomConcurrencyIntegrationTest::await).toList();
        assertThat(results.stream().filter(Result::success).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.errorCode() == ErrorCode.USER_ALREADY_IN_ROOM).count()).isEqualTo(1);
        assertThat(playerRepository.findActiveByUserId(contender.id())).isPresent();
    }

    @Test
    void concurrentOwnerLeaveAndJoinKeepsExactlyOneOwner() throws Exception {
        User owner = user("leave-owner@example.com", "leaveOwner");
        User nextOwner = user("next-owner@example.com", "nextOwner");
        User joining = user("joining@example.com", "joining");
        long roomId = commandService.create(owner.id(), "위임경쟁", 4, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, nextOwner.id());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = List.of(
            executor.submit(task(start, () -> commandService.leave(roomId, owner.id()))),
            executor.submit(task(start, () -> commandService.join(roomId, joining.id())))
        );
        start.countDown();

        List<Result> results = futures.stream().map(RoomConcurrencyIntegrationTest::await).toList();
        assertThat(results).allMatch(Result::success);
        assertThat(playerRepository.countActiveByRoomId(roomId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM room_players WHERE room_id=? AND left_at IS NULL AND is_owner=TRUE",
            Integer.class, roomId)).isEqualTo(1);
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now));
    }

    private Callable<Result> task(CountDownLatch start, ThrowingRunnable action) {
        return () -> {
            start.await();
            try {
                action.run();
                return new Result(true, null);
            } catch (BusinessException exception) {
                return new Result(false, exception.errorCode());
            }
        };
    }

    private static Result await(Future<Result> future) {
        try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private record Result(boolean success, ErrorCode errorCode) {}
    @FunctionalInterface private interface ThrowingRunnable { void run(); }
}
