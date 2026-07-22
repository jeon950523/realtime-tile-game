package com.realtimetilegame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class GameV4MigrationIntegrationTest {
    @Test
    void v4BackfillsAnExistingPhase4GameAndEnforcesTurnRuntimeConstraints() {
        String url = "jdbc:h2:mem:v4_backfill;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("3"))
            .load()
            .migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 15, 13, 0);

        jdbcTemplate.update(
            """
                INSERT INTO users (
                    id, email, password, nickname, avatar_type, rating_score, status,
                    created_at, updated_at, deleted_at
                ) VALUES (1, 'phase4@example.com', 'encoded', 'phase4', 'DEFAULT_01', 1000, 'ACTIVE', ?, ?, NULL)
                """,
            Timestamp.valueOf(startedAt),
            Timestamp.valueOf(startedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO rooms (
                    id, room_name, owner_user_id, max_players, game_mode, turn_time_limit_seconds,
                    game_time_limit_seconds, is_public, status, created_at, updated_at, closed_at
                ) VALUES (1, '기존게임방', 1, 2, 'CLASSIC', 120, NULL, TRUE, 'PLAYING', ?, ?, NULL)
                """,
            Timestamp.valueOf(startedAt),
            Timestamp.valueOf(startedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO games (
                    id, room_id, game_mode, status, current_turn_user_id, current_turn_seat_order,
                    turn_number, started_at, updated_at, finished_at, version
                ) VALUES (1, 1, 'CLASSIC', 'IN_PROGRESS', 1, 1, 1, ?, ?, NULL, 0)
                """,
            Timestamp.valueOf(startedAt),
            Timestamp.valueOf(startedAt)
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_turn_id FROM games WHERE id = 1", String.class
        )).matches("^[0-9a-fA-F-]{36}$");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_turn_started_at FROM games WHERE id = 1", LocalDateTime.class
        )).isEqualTo(startedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_turn_deadline_at FROM games WHERE id = 1", LocalDateTime.class
        )).isEqualTo(startedAt.plusSeconds(120));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT consecutive_pass_count FROM games WHERE id = 1", Integer.class
        )).isZero();

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE games SET consecutive_pass_count = -1 WHERE id = 1"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE games SET current_turn_deadline_at = current_turn_started_at WHERE id = 1"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE games SET current_turn_id = NULL WHERE id = 1"
        )).isInstanceOf(DataAccessException.class);
    }
}
