package com.realtimetilegame.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.realtimetilegame.auth.domain.RefreshToken;
import com.realtimetilegame.auth.domain.RefreshTokenRepository;
import com.realtimetilegame.auth.infrastructure.RefreshTokenHasher;
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
class MigrationAndRepositoryIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenHasher hasher;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void migrationCreatesRequiredTablesAndHibernateValidateStarts() {
        Integer usersTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'users'",
            Integer.class
        );
        Integer roomsTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'rooms'",
            Integer.class
        );
        Integer roomPlayersTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'room_players'",
            Integer.class
        );
        Integer refreshTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'refresh_tokens'",
            Integer.class
        );
        Integer gamesTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'games'",
            Integer.class
        );
        Integer gamePlayersTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'game_players'",
            Integer.class
        );
        Integer gameTilesTable = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'game_tiles'",
            Integer.class
        );
        assertThat(usersTable).isEqualTo(1);
        assertThat(refreshTable).isEqualTo(1);
        assertThat(roomsTable).isEqualTo(1);
        assertThat(roomPlayersTable).isEqualTo(1);
        assertThat(gamesTable).isEqualTo(1);
        assertThat(gamePlayersTable).isEqualTo(1);
        assertThat(gameTilesTable).isEqualTo(1);
    }

    @Test
    void userRepositoryLooksUpEmailAndNicknameIgnoringCase() {
        LocalDateTime now = LocalDateTime.now();
        userRepository.saveAndFlush(User.register(
            "user@example.com",
            passwordEncoder.encode("qwer1234!"),
            "PlayerOne",
            now
        ));

        assertThat(userRepository.findByEmailIgnoreCase("USER@EXAMPLE.COM")).isPresent();
        assertThat(userRepository.existsByNicknameIgnoreCase("playerone")).isTrue();
    }

    @Test
    void refreshRepositoryStoresOnlyHashValue() {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.saveAndFlush(User.register(
            "user@example.com",
            passwordEncoder.encode("qwer1234!"),
            "player1",
            now
        ));
        String raw = "opaque-refresh-token-value";
        String hash = hasher.hash(raw);
        refreshTokenRepository.save(RefreshToken.issue(user, hash, now.plusDays(30), now));

        String stored = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(stored).isEqualTo(hash);
        assertThat(stored).doesNotContain(raw);
    }
}
