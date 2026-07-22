ALTER TABLE games ADD COLUMN termination_reason VARCHAR(30) NULL;

ALTER TABLE games ADD COLUMN winner_user_id BIGINT NULL;

ALTER TABLE games ADD CONSTRAINT fk_games_winner_user
    FOREIGN KEY (winner_user_id) REFERENCES users(id);

ALTER TABLE game_players
    ADD COLUMN participant_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
