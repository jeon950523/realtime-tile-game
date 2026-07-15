package com.realtimetilegame.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.realtimetilegame.auth.application.AuthService;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.support.DatabaseCleanup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenConcurrencyIntegrationTest {
    @Autowired
    private AuthService authService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void concurrentReissueAllowsExactlyOneRotation() throws Exception {
        authService.register("user@example.com", "qwer1234!", "qwer1234!", "player1");
        String refreshToken = authService.login("user@example.com", "qwer1234!").refreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt += 1) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    authService.reissue(refreshToken);
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            }));
        }
        ready.await();
        start.countDown();

        long successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(20, TimeUnit.SECONDS)) {
                successes += 1;
            }
        }
        assertThat(successes).isEqualTo(1);
        Integer revokedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL",
            Integer.class
        );
        Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL",
            Integer.class
        );
        assertThat(revokedCount).isEqualTo(1);
        assertThat(activeCount).isEqualTo(1);
    }
}
