package com.realtimetilegame.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class DatabaseCleanup {
    private DatabaseCleanup() {
    }

    public static void clear(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM game_tiles");
        jdbcTemplate.update("DELETE FROM game_players");
        jdbcTemplate.update("DELETE FROM games");
        jdbcTemplate.update("DELETE FROM room_players");
        jdbcTemplate.update("DELETE FROM rooms");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}
