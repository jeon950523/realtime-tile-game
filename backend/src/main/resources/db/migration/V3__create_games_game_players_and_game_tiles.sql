CREATE TABLE games (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    game_mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_turn_user_id BIGINT NOT NULL,
    current_turn_seat_order TINYINT NOT NULL,
    turn_number INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_games PRIMARY KEY (id),
    CONSTRAINT uk_games_room UNIQUE (room_id),
    CONSTRAINT fk_games_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_games_current_turn_user FOREIGN KEY (current_turn_user_id) REFERENCES users(id),
    CONSTRAINT chk_games_turn_number CHECK (turn_number >= 1),
    CONSTRAINT chk_games_current_turn_seat CHECK (current_turn_seat_order BETWEEN 1 AND 4)
);

CREATE INDEX idx_games_status_started ON games(status, started_at);
CREATE INDEX idx_games_current_turn_user ON games(current_turn_user_id, status);

CREATE TABLE game_players (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_order TINYINT NOT NULL,
    initial_meld_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_game_players PRIMARY KEY (id),
    CONSTRAINT uk_game_players_game_user UNIQUE (game_id, user_id),
    CONSTRAINT uk_game_players_game_seat UNIQUE (game_id, seat_order),
    CONSTRAINT fk_game_players_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_game_players_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_game_players_seat CHECK (seat_order BETWEEN 1 AND 4)
);

CREATE INDEX idx_game_players_user_game ON game_players(user_id, game_id);

CREATE TABLE game_tiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    tile_id VARCHAR(32) NOT NULL,
    location VARCHAR(20) NOT NULL,
    owner_game_player_id BIGINT NULL,
    position_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_game_tiles PRIMARY KEY (id),
    CONSTRAINT uk_game_tiles_game_tile UNIQUE (game_id, tile_id),
    CONSTRAINT fk_game_tiles_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_game_tiles_owner FOREIGN KEY (owner_game_player_id) REFERENCES game_players(id),
    CONSTRAINT chk_game_tiles_position CHECK (position_order >= 0),
    CONSTRAINT chk_game_tiles_owner_by_location CHECK (
        (location = 'RACK' AND owner_game_player_id IS NOT NULL)
        OR (location IN ('POOL', 'TABLE') AND owner_game_player_id IS NULL)
    )
);

CREATE INDEX idx_game_tiles_game_location_position
    ON game_tiles(game_id, location, position_order);
CREATE INDEX idx_game_tiles_owner_position
    ON game_tiles(owner_game_player_id, position_order);
