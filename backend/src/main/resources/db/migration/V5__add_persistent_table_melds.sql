CREATE TABLE game_melds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    meld_id VARCHAR(36) NOT NULL,
    position_order INT NOT NULL,
    meld_type VARCHAR(20) NOT NULL,
    score INT NOT NULL,
    created_by_game_player_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_game_melds PRIMARY KEY (id),
    CONSTRAINT uk_game_melds_game_meld UNIQUE (game_id, meld_id),
    CONSTRAINT uk_game_melds_game_position UNIQUE (game_id, position_order),
    CONSTRAINT fk_game_melds_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_game_melds_created_by FOREIGN KEY (created_by_game_player_id) REFERENCES game_players(id),
    CONSTRAINT chk_game_melds_position CHECK (position_order >= 0),
    CONSTRAINT chk_game_melds_score CHECK (score >= 0)
);

CREATE INDEX idx_game_melds_game_position
    ON game_melds(game_id, position_order);

ALTER TABLE game_tiles ADD COLUMN game_meld_id BIGINT NULL;
ALTER TABLE game_tiles ADD CONSTRAINT fk_game_tiles_meld
    FOREIGN KEY (game_meld_id) REFERENCES game_melds(id);
ALTER TABLE game_tiles ADD CONSTRAINT uk_game_tiles_meld_position
    UNIQUE (game_meld_id, position_order);
CREATE INDEX idx_game_tiles_meld_position
    ON game_tiles(game_meld_id, position_order);

ALTER TABLE game_tiles DROP CONSTRAINT chk_game_tiles_owner_by_location;
ALTER TABLE game_tiles ADD CONSTRAINT chk_game_tiles_location_links CHECK (
    (location = 'RACK' AND owner_game_player_id IS NOT NULL AND game_meld_id IS NULL)
    OR (location = 'POOL' AND owner_game_player_id IS NULL AND game_meld_id IS NULL)
    OR (location = 'TABLE' AND owner_game_player_id IS NULL AND game_meld_id IS NOT NULL)
);
