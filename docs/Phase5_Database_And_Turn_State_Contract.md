# Phase 5 Database And Turn State Contract

작성 기준: 2026-07-15 KST

## 1. Flyway V4

파일:

```text
backend/src/main/resources/db/migration/V4__add_game_turn_runtime_columns.sql
```

V1·V2·V3는 수정하지 않는다.

### 추가 컬럼

| 컬럼 | 타입 | 조건 |
|---|---|---|
| `current_turn_id` | `VARCHAR(36)` | NOT NULL, UNIQUE |
| `current_turn_started_at` | `DATETIME(6)` | NOT NULL |
| `current_turn_deadline_at` | `DATETIME(6)` | NOT NULL, started보다 큼 |
| `consecutive_pass_count` | `INT` | NOT NULL DEFAULT 0, 0 이상 |

### 기존 Row Backfill

```text
current_turn_id = UUID()
current_turn_started_at = games.started_at
current_turn_deadline_at = games.started_at + rooms.turn_time_limit_seconds
consecutive_pass_count = 0
```

Volume 삭제나 DB 초기화를 전제로 하지 않는다.

## 2. Game 불변식

```text
status = IN_PROGRESS인 경우에만 Turn 전이
Seat 1..4
turnNumber >= 1
currentTurnUser와 currentTurnSeatOrder가 같은 GamePlayer를 가리킴
currentTurnId는 UUID 문자열
deadline > started
consecutivePassCount >= 0
```

Draw 성공:

```text
turnNumber +1
새 currentTurnId
startedAt = now
deadline = now + Room.turnTimeLimitSeconds
consecutivePassCount = 0
```

PASS 성공:

```text
turnNumber +1
새 currentTurnId
startedAt = now
deadline = now + Room.turnTimeLimitSeconds
consecutivePassCount +1
```

## 3. GameTile Draw 계약

허용 전 상태:

```text
location = POOL
owner = NULL
```

성공 후:

```text
location = RACK
owner = current GamePlayer
position_order = 해당 Rack max + 1
updated_at = now
```

다른 Game의 Player, 이미 RACK/TABLE인 Tile, 음수 position은 거부한다.

## 4. 전체 Tile 정합성

Phase 5 Snapshot은 다음을 검증한다.

- GameTile 총 106개
- tileId 중복 0
- 위치는 RACK 또는 POOL만 허용
- 모든 참가자 Rack은 초기 14개 이상
- `Rack 합 + Pool = 106`
- Pool position 중복 0
- Player별 Rack position 중복 0
- Rack owner는 해당 Game 참가자
- Pool owner는 NULL

## 5. Public State

REST·Private Event 내부 Public State 공통 필드:

```text
gameVersion
currentTurnUserId
currentTurnSeatOrder
turnNumber
currentTurnId
currentTurnStartedAt
turnDeadlineAt
consecutivePassCount
tilePoolCount
players[].rackTileCount
```

상대 Rack의 Tile ID·색상·숫자·조커 여부는 포함하지 않는다.

## 6. SQL 직접 검증

```sql
SELECT id, version, current_turn_user_id, current_turn_seat_order,
       turn_number, current_turn_id, current_turn_started_at,
       current_turn_deadline_at, consecutive_pass_count
FROM games
WHERE id = ?;

SELECT location, COUNT(*)
FROM game_tiles
WHERE game_id = ?
GROUP BY location;

SELECT gp.user_id, COUNT(*) AS rack_count,
       MIN(gt.position_order) AS min_position,
       MAX(gt.position_order) AS max_position
FROM game_players gp
JOIN game_tiles gt
  ON gt.owner_game_player_id = gp.id
 AND gt.location = 'RACK'
WHERE gp.game_id = ?
GROUP BY gp.id, gp.user_id, gp.seat_order
ORDER BY gp.seat_order;

SELECT COUNT(*) AS total_count,
       COUNT(DISTINCT tile_id) AS distinct_tile_count
FROM game_tiles
WHERE game_id = ?;
```
