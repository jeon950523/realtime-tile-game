ALTER TABLE game_melds ADD COLUMN last_modified_by_game_player_id BIGINT NULL;

UPDATE game_melds
SET last_modified_by_game_player_id = created_by_game_player_id
WHERE last_modified_by_game_player_id IS NULL;

ALTER TABLE game_melds MODIFY COLUMN last_modified_by_game_player_id BIGINT NOT NULL;
ALTER TABLE game_melds ADD CONSTRAINT fk_game_melds_last_modified_by
    FOREIGN KEY (last_modified_by_game_player_id) REFERENCES game_players(id);

CREATE INDEX idx_game_melds_last_modified_by
    ON game_melds(last_modified_by_game_player_id);
