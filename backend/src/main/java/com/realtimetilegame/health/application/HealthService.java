package com.realtimetilegame.health.application;

import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.common.error.ServiceUnavailableException;
import com.realtimetilegame.health.presentation.dto.HealthResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private static final String APPLICATION_NAME = "realtime-tile-game-backend";

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse check() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result == null || result != 1) {
                throw new IllegalStateException("Unexpected database health query result");
            }
            return new HealthResponse(APPLICATION_NAME, "UP", "UP");
        } catch (DataAccessException | IllegalStateException exception) {
            throw new ServiceUnavailableException(ErrorCode.DATABASE_UNAVAILABLE, exception);
        }
    }
}
