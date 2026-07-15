package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.event.GameStartedCommittedEvent;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.domain.RoomRepository;
import com.realtimetilegame.room.domain.RoomStatus;
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
@Import(GameEventAtomicityIntegrationTest.EventProbeConfiguration.class)
class GameEventAtomicityIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired RoomRepository roomRepository;
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
    void gameStartedEventRunsAfterACommittedGameOnly() {
        Fixture fixture = readyRoom("commit");

        gameStartService.startGame(fixture.roomId(), fixture.owner().id());

        assertThat(eventProbe.afterCommitCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_tiles", Integer.class)).isEqualTo(106);
    }

    @Test
    void beforeCommitFailureRollsBackRoomGamePlayersTilesAndAfterCommitEvent() {
        Fixture fixture = readyRoom("rollback");
        eventProbe.failBeforeCommitOnce();

        assertThatThrownBy(() -> gameStartService.startGame(fixture.roomId(), fixture.owner().id()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("forced before-commit failure");

        assertThat(eventProbe.afterCommitCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_players", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_tiles", Integer.class)).isZero();
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().status()).isEqualTo(RoomStatus.WAITING);
    }

    private Fixture readyRoom(String prefix) {
        User owner = user(prefix + "-owner@example.com", prefix + "Owner");
        User second = user(prefix + "-second@example.com", prefix + "Second");
        long roomId = roomCommandService.create(owner.id(), "이벤트원자성방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);
        return new Fixture(roomId, owner);
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private record Fixture(long roomId, User owner) {
    }

    @TestConfiguration
    static class EventProbeConfiguration {
        @Bean
        EventProbe eventProbe() {
            return new EventProbe();
        }
    }

    static final class EventProbe {
        private final AtomicBoolean failBeforeCommit = new AtomicBoolean();
        private final AtomicInteger afterCommit = new AtomicInteger();

        void reset() {
            failBeforeCommit.set(false);
            afterCommit.set(0);
        }

        void failBeforeCommitOnce() {
            failBeforeCommit.set(true);
        }

        int afterCommitCount() {
            return afterCommit.get();
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void beforeCommit(GameStartedCommittedEvent event) {
            if (failBeforeCommit.compareAndSet(true, false)) {
                throw new IllegalStateException("forced before-commit failure");
            }
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void afterCommit(GameStartedCommittedEvent event) {
            afterCommit.incrementAndGet();
        }
    }
}
