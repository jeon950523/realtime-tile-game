ALTER TABLE games ADD COLUMN current_turn_id VARCHAR(36) NULL;
ALTER TABLE games ADD COLUMN current_turn_started_at DATETIME(6) NULL;
ALTER TABLE games ADD COLUMN current_turn_deadline_at DATETIME(6) NULL;
ALTER TABLE games ADD COLUMN consecutive_pass_count INT NOT NULL DEFAULT 0;

UPDATE games
SET current_turn_id = UUID(),
    current_turn_started_at = started_at,
    current_turn_deadline_at = TIMESTAMPADD(
        SECOND,
        (SELECT rooms.turn_time_limit_seconds FROM rooms WHERE rooms.id = games.room_id),
        started_at
    )
WHERE current_turn_id IS NULL;

ALTER TABLE games MODIFY COLUMN current_turn_id VARCHAR(36) NOT NULL;
ALTER TABLE games MODIFY COLUMN current_turn_started_at DATETIME(6) NOT NULL;
ALTER TABLE games MODIFY COLUMN current_turn_deadline_at DATETIME(6) NOT NULL;

ALTER TABLE games ADD CONSTRAINT uk_games_current_turn_id UNIQUE (current_turn_id);
ALTER TABLE games ADD CONSTRAINT chk_games_consecutive_pass_count CHECK (consecutive_pass_count >= 0);
ALTER TABLE games ADD CONSTRAINT chk_games_turn_deadline CHECK (current_turn_deadline_at > current_turn_started_at);
