ALTER TABLE game_melds ADD COLUMN grid_row INT NULL;
ALTER TABLE game_melds ADD COLUMN grid_column INT NULL;

UPDATE game_melds
SET grid_row = FLOOR(position_order / 2),
    grid_column = MOD(position_order, 2) * 13;

ALTER TABLE game_melds MODIFY COLUMN grid_row INT NOT NULL;
ALTER TABLE game_melds MODIFY COLUMN grid_column INT NOT NULL;
ALTER TABLE game_melds ADD CONSTRAINT chk_game_melds_grid_row
    CHECK (grid_row >= 0 AND grid_row < 18);
ALTER TABLE game_melds ADD CONSTRAINT chk_game_melds_grid_column
    CHECK (grid_column >= 0 AND grid_column < 26);

CREATE INDEX idx_game_melds_game_grid
    ON game_melds(game_id, grid_row, grid_column);
