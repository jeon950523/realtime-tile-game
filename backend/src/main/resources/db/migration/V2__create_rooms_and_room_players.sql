CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(50) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    max_players TINYINT NOT NULL,
    game_mode VARCHAR(20) NOT NULL,
    turn_time_limit_seconds INT NOT NULL,
    game_time_limit_seconds INT NULL,
    is_public BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    CONSTRAINT fk_rooms_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT chk_rooms_max_players CHECK (max_players BETWEEN 2 AND 4),
    CONSTRAINT chk_rooms_turn_limit CHECK (turn_time_limit_seconds BETWEEN 30 AND 300)
);

CREATE INDEX idx_rooms_status_created ON rooms(status, created_at);
CREATE INDEX idx_rooms_game_mode_status ON rooms(game_mode, status);
CREATE INDEX idx_rooms_owner_status ON rooms(owner_user_id, status);

CREATE TABLE room_players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_order TINYINT NOT NULL,
    ready_status VARCHAR(20) NOT NULL,
    is_owner BOOLEAN NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    CONSTRAINT fk_room_players_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_room_players_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_room_players_seat CHECK (seat_order BETWEEN 1 AND 4)
);

CREATE INDEX idx_room_players_room_active_seat ON room_players(room_id, left_at, seat_order);
CREATE INDEX idx_room_players_user_active ON room_players(user_id, left_at);
CREATE INDEX idx_room_players_room_user_active ON room_players(room_id, user_id, left_at);
