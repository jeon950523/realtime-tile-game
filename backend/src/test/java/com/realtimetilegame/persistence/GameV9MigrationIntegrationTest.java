package com.realtimetilegame.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class GameV9MigrationIntegrationTest {
    @Test
    void beP7E002BackfillsExistingMeldCreatorAsLastModifier() {
        String url = "jdbc:h2:mem:v9_meld_last_modifier;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("8"))
            .load()
            .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime nowValue = LocalDateTime.of(2026, 7, 23, 14, 0);
        Timestamp now = Timestamp.valueOf(nowValue);
        Timestamp deadline = Timestamp.valueOf(nowValue.plusSeconds(120));

        jdbc.update("INSERT INTO users (id,email,password,nickname,avatar_type,rating_score,status,created_at,updated_at) VALUES (1,'modifier@example.com','encoded','modifier','DEFAULT_01',1000,'ACTIVE',?,?)", now, now);
        jdbc.update("INSERT INTO rooms (id,room_name,owner_user_id,max_players,game_mode,turn_time_limit_seconds,is_public,status,created_at,updated_at) VALUES (1,'modifier',1,2,'CLASSIC',120,TRUE,'PLAYING',?,?)", now, now);
        jdbc.update("INSERT INTO games (id,room_id,game_mode,status,current_turn_user_id,current_turn_seat_order,turn_number,started_at,updated_at,version,current_turn_id,current_turn_started_at,current_turn_deadline_at,consecutive_pass_count) VALUES (1,1,'CLASSIC','IN_PROGRESS',1,1,1,?,?,0,'11111111-1111-4111-8111-111111111111',?,?,0)", now, now, now, deadline);
        jdbc.update("INSERT INTO game_players (id,game_id,user_id,seat_order,initial_meld_completed,participant_status,created_at) VALUES (1,1,1,1,TRUE,'ACTIVE',?)", now);
        jdbc.update("INSERT INTO game_melds (game_id,meld_id,position_order,grid_row,grid_column,meld_type,score,created_by_game_player_id,created_at,updated_at) VALUES (1,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',0,0,0,'RUN',24,1,?,?)", now, now);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject(
            "SELECT last_modified_by_game_player_id FROM game_melds WHERE id = 1",
            Long.class
        )).isEqualTo(1L);
    }
}
