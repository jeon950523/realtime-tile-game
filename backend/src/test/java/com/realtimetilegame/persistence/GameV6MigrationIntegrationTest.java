package com.realtimetilegame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class GameV6MigrationIntegrationTest {
    @Test
    void beP7B004BackfillsExistingMeldPositionsIntoNonOverlappingGridCoordinates() {
        String url = "jdbc:h2:mem:v6_grid_backfill;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("5"))
            .load()
            .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        Timestamp timestamp = Timestamp.valueOf(now);

        jdbc.update("INSERT INTO users (id,email,password,nickname,avatar_type,rating_score,status,created_at,updated_at) VALUES (1,'grid@example.com','encoded','grid','DEFAULT_01',1000,'ACTIVE',?,?)", timestamp, timestamp);
        jdbc.update("INSERT INTO rooms (id,room_name,owner_user_id,max_players,game_mode,turn_time_limit_seconds,is_public,status,created_at,updated_at) VALUES (1,'grid',1,2,'CLASSIC',120,TRUE,'PLAYING',?,?)", timestamp, timestamp);
        jdbc.update("INSERT INTO games (id,room_id,game_mode,status,current_turn_user_id,current_turn_seat_order,turn_number,started_at,updated_at,version,current_turn_id,current_turn_started_at,current_turn_deadline_at,consecutive_pass_count) VALUES (1,1,'CLASSIC','IN_PROGRESS',1,1,1,?,?,0,'11111111-1111-4111-8111-111111111111',?,?,0)", timestamp, timestamp, timestamp, Timestamp.valueOf(now.plusSeconds(120)));
        jdbc.update("INSERT INTO game_players (id,game_id,user_id,seat_order,initial_meld_completed,created_at) VALUES (1,1,1,1,TRUE,?)", timestamp);
        for (int position = 0; position < 3; position++) {
            jdbc.update("INSERT INTO game_melds (game_id,meld_id,position_order,meld_type,score,created_by_game_player_id,created_at,updated_at) VALUES (1,?,?,'RUN',6,1,?,?)",
                "00000000-0000-4000-8000-00000000000" + position, position, timestamp, timestamp);
        }

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        List<String> coordinates = jdbc.query(
            "SELECT grid_row, grid_column FROM game_melds ORDER BY position_order",
            (rs, row) -> rs.getInt(1) + ":" + rs.getInt(2)
        );
        assertThat(coordinates).containsExactly("0:0", "0:13", "1:0");
        assertThatThrownBy(() -> jdbc.update("UPDATE game_melds SET grid_row = -1 WHERE position_order = 0"))
            .isInstanceOf(DataAccessException.class);
    }
}
