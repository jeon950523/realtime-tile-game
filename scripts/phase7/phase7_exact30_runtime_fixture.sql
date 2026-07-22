-- Phase 7 local runtime fixture.
-- Targets the newest IN_PROGRESS game and gives the named current player
-- RED 7-8-9 (24) plus BLUE 1-2-3 (6), with eight filler tiles.
-- This script is for local acceptance verification only.

START TRANSACTION;

SET @phase7_game_id = (
    SELECT id FROM games WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1
);
SET @phase7_user_id = (
    SELECT id FROM users WHERE email = 'phase7.runtime.b.20260718.0312@example.test' LIMIT 1
);
SET @phase7_player_id = (
    SELECT id FROM game_players
    WHERE game_id = @phase7_game_id AND user_id = @phase7_user_id
    LIMIT 1
);
SET @phase7_seat_order = (
    SELECT seat_order FROM game_players WHERE id = @phase7_player_id
);

UPDATE games
SET current_turn_user_id = @phase7_user_id,
    current_turn_seat_order = @phase7_seat_order,
    current_turn_id = UUID(),
    current_turn_started_at = UTC_TIMESTAMP(6),
    current_turn_deadline_at = TIMESTAMPADD(SECOND, 180, UTC_TIMESTAMP(6)),
    updated_at = UTC_TIMESTAMP(6)
WHERE id = @phase7_game_id;

UPDATE game_tiles
SET location = 'POOL',
    owner_game_player_id = NULL,
    game_meld_id = NULL,
    position_order = 200000 + id,
    updated_at = UTC_TIMESTAMP(6)
WHERE game_id = @phase7_game_id
  AND owner_game_player_id = @phase7_player_id
  AND location = 'RACK';

CREATE TEMPORARY TABLE phase7_runtime_targets (
    tile_id VARCHAR(32) PRIMARY KEY,
    rack_position INT NOT NULL
);

INSERT INTO phase7_runtime_targets (tile_id, rack_position) VALUES
    ('RED-07-A', 0),
    ('RED-08-A', 1),
    ('RED-09-A', 2),
    ('BLUE-01-A', 3),
    ('BLUE-02-A', 4),
    ('BLUE-03-A', 5);

UPDATE game_tiles tile
JOIN phase7_runtime_targets target ON target.tile_id = tile.tile_id
SET tile.location = 'RACK',
    tile.owner_game_player_id = @phase7_player_id,
    tile.game_meld_id = NULL,
    tile.position_order = target.rack_position,
    tile.updated_at = UTC_TIMESTAMP(6)
WHERE tile.game_id = @phase7_game_id;

CREATE TEMPORARY TABLE phase7_runtime_fillers AS
SELECT tile.id AS game_tile_id,
       ROW_NUMBER() OVER (ORDER BY tile.position_order, tile.id) + 5 AS rack_position
FROM game_tiles tile
WHERE tile.game_id = @phase7_game_id
  AND tile.location = 'POOL'
  AND tile.tile_id NOT IN (SELECT tile_id FROM phase7_runtime_targets)
ORDER BY tile.position_order, tile.id
LIMIT 8;

UPDATE game_tiles tile
JOIN phase7_runtime_fillers filler ON filler.game_tile_id = tile.id
SET tile.location = 'RACK',
    tile.owner_game_player_id = @phase7_player_id,
    tile.game_meld_id = NULL,
    tile.position_order = filler.rack_position,
    tile.updated_at = UTC_TIMESTAMP(6);

CREATE TEMPORARY TABLE phase7_runtime_rack_order AS
SELECT tile.id AS game_tile_id,
       ROW_NUMBER() OVER (
           PARTITION BY tile.owner_game_player_id
           ORDER BY tile.position_order, tile.id
       ) - 1 AS rack_position
FROM game_tiles tile
WHERE tile.game_id = @phase7_game_id
  AND tile.location = 'RACK';

UPDATE game_tiles tile
JOIN phase7_runtime_rack_order rack_order ON rack_order.game_tile_id = tile.id
SET tile.position_order = rack_order.rack_position,
    tile.updated_at = UTC_TIMESTAMP(6);

COMMIT;

SELECT game.id AS game_id,
       game.version AS game_version,
       game.current_turn_user_id,
       player.initial_meld_completed,
       COUNT(tile.id) AS current_player_rack_count
FROM games game
JOIN game_players player
  ON player.game_id = game.id AND player.user_id = game.current_turn_user_id
JOIN game_tiles tile
  ON tile.owner_game_player_id = player.id AND tile.location = 'RACK'
WHERE game.id = @phase7_game_id
GROUP BY game.id, game.version, game.current_turn_user_id, player.initial_meld_completed;

SELECT tile.tile_id, tile.position_order
FROM game_tiles tile
WHERE tile.game_id = @phase7_game_id
  AND tile.owner_game_player_id = @phase7_player_id
  AND tile.location = 'RACK'
ORDER BY tile.position_order;
