package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GamePersistenceConstraintIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;

    private User owner;
    private User second;
    private GameStartResult result;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        owner = user("constraint-owner@example.com", "constraintOwner");
        second = user("constraint-second@example.com", "constraintSecond");
        long roomId = roomCommandService.create(owner.id(), "제약검증방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);
        result = gameStartService.startGame(roomId, owner.id());
    }

    @Test
    void gamesRoomIdIsUnique() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO games (
                    room_id, game_mode, status, current_turn_user_id, current_turn_seat_order,
                    turn_number, current_turn_id, current_turn_started_at, current_turn_deadline_at,
                    consecutive_pass_count, started_at, updated_at, finished_at, version
                )
                SELECT room_id, game_mode, status, current_turn_user_id, current_turn_seat_order,
                       turn_number, '99999999-9999-4999-8999-999999999999', current_turn_started_at,
                       current_turn_deadline_at, consecutive_pass_count, started_at, updated_at,
                       finished_at, version
                FROM games WHERE id = ?
                """,
            result.gameId()
        )).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM games WHERE room_id = ?", Integer.class, result.roomId()
        )).isEqualTo(1);
    }

    @Test
    void gamePlayerUserAndSeatAreUniqueInsideOneGame() {
        Long firstPlayerId = jdbcTemplate.queryForObject(
            "SELECT id FROM game_players WHERE game_id = ? AND seat_order = 1",
            Long.class,
            result.gameId()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO game_players (game_id, user_id, seat_order, initial_meld_completed, created_at)
                VALUES (?, ?, 3, FALSE, CURRENT_TIMESTAMP)
                """,
            result.gameId(),
            owner.id()
        )).isInstanceOf(DataAccessException.class);

        User outsider = user("constraint-outsider@example.com", "constraintOutsider");
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO game_players (game_id, user_id, seat_order, initial_meld_completed, created_at)
                VALUES (?, ?, 1, FALSE, CURRENT_TIMESTAMP)
                """,
            result.gameId(),
            outsider.id()
        )).isInstanceOf(DataAccessException.class);

        assertThat(firstPlayerId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_players WHERE game_id = ?", Integer.class, result.gameId()
        )).isEqualTo(2);
    }

    @Test
    void gameTileIdIsUniqueAndLocationOwnerConstraintIsEnforced() {
        Long ownerPlayerId = jdbcTemplate.queryForObject(
            "SELECT id FROM game_players WHERE game_id = ? AND user_id = ?",
            Long.class,
            result.gameId(),
            owner.id()
        );
        String existingTileId = jdbcTemplate.queryForObject(
            "SELECT tile_id FROM game_tiles WHERE game_id = ? FETCH FIRST 1 ROW ONLY",
            String.class,
            result.gameId()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO game_tiles (
                    game_id, tile_id, location, owner_game_player_id, position_order, created_at, updated_at
                ) VALUES (?, ?, 'RACK', ?, 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            result.gameId(),
            existingTileId,
            ownerPlayerId
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO game_tiles (
                    game_id, tile_id, location, owner_game_player_id, position_order, created_at, updated_at
                ) VALUES (?, 'INVALID-POOL-OWNER', 'POOL', ?, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            result.gameId(),
            ownerPlayerId
        )).isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_tiles WHERE game_id = ?", Integer.class, result.gameId()
        )).isEqualTo(106);
    }

    @Test
    void v5TableMeldIdentifiersPositionsAndTileLinksAreConstrained() {
        Long creatorId = jdbcTemplate.queryForObject(
            "SELECT id FROM game_players WHERE game_id = ? AND user_id = ?", Long.class, result.gameId(), owner.id()
        );
        jdbcTemplate.update(
            "INSERT INTO game_melds (game_id, meld_id, position_order, grid_row, grid_column, meld_type, score, created_by_game_player_id, created_at, updated_at) "
                + "VALUES (?, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 0, 0, 0, 'RUN', 24, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            result.gameId(), creatorId
        );
        Long meldId = jdbcTemplate.queryForObject(
            "SELECT id FROM game_melds WHERE game_id = ? AND position_order = 0", Long.class, result.gameId()
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO game_melds (game_id, meld_id, position_order, grid_row, grid_column, meld_type, score, created_by_game_player_id, created_at, updated_at) "
                + "VALUES (?, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 1, 0, 13, 'GROUP', 21, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            result.gameId(), creatorId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO game_melds (game_id, meld_id, position_order, grid_row, grid_column, meld_type, score, created_by_game_player_id, created_at, updated_at) "
                + "VALUES (?, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 0, 1, 0, 'GROUP', 21, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            result.gameId(), creatorId
        )).isInstanceOf(DataAccessException.class);

        List<Long> tileRows = jdbcTemplate.queryForList(
            "SELECT id FROM game_tiles WHERE game_id = ? ORDER BY id FETCH FIRST 2 ROWS ONLY", Long.class, result.gameId()
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'TABLE', owner_game_player_id = NULL, game_meld_id = NULL WHERE id = ?",
            tileRows.get(0)
        )).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'TABLE', owner_game_player_id = NULL, game_meld_id = ?, position_order = 0 WHERE id = ?",
            meldId, tileRows.get(0)
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE game_tiles SET location = 'TABLE', owner_game_player_id = NULL, game_meld_id = ?, position_order = 0 WHERE id = ?",
            meldId, tileRows.get(1)
        )).isInstanceOf(DataAccessException.class);
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }
}
