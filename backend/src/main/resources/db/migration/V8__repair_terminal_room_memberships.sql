-- Repair room state left inconsistent by the first active-game exit implementation.
-- A terminal game must never leave its room open or keep active room memberships.

UPDATE rooms
SET status = 'CLOSED',
    closed_at = COALESCE(closed_at, CURRENT_TIMESTAMP(6)),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE status <> 'CLOSED'
  AND id IN (
      SELECT g.room_id
      FROM games g
      WHERE g.status IN ('FINISHED', 'ABORTED')
  );

UPDATE room_players
SET left_at = COALESCE(left_at, CURRENT_TIMESTAMP(6)),
    is_owner = FALSE
WHERE left_at IS NULL
  AND room_id IN (
      SELECT r.id
      FROM rooms r
      LEFT JOIN games g ON g.room_id = r.id
      WHERE r.status = 'CLOSED'
         OR g.status IN ('FINISHED', 'ABORTED')
  );
