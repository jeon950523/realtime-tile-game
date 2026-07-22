# Phase 4 DB·게임 상태 계약

작성 기준: 2026-07-15 KST

## V3 Migration

신규 파일:

```text
backend/src/main/resources/db/migration/V3__create_games_game_players_and_game_tiles.sql
```

V1·V2는 수정하지 않았다.

## games

- 한 Room당 Game 1개: `UNIQUE(room_id)`
- `game_mode = CLASSIC`
- `status = IN_PROGRESS`
- `turn_number = 1`
- `current_turn_user_id`와 `current_turn_seat_order` 저장
- `finished_at = NULL`
- `@Version` 대응 `version` 컬럼

## game_players

RoomPlayer의 게임 시작 시점 Snapshot이다.

- `UNIQUE(game_id, user_id)`
- `UNIQUE(game_id, seat_order)`
- seatOrder 1..4
- `initial_meld_completed = FALSE`

게임 시작 후 RoomPlayer의 대기방 상태 변화에 게임 참가자 구성이 의존하지 않는다.

## game_tiles

- `UNIQUE(game_id, tile_id)`
- Phase 4 생성 Location: RACK, POOL
- TABLE은 후속 Phase 호환 값으로만 예약
- RACK은 Owner 필수
- POOL/TABLE은 Owner 금지
- `position_order >= 0`

색상·숫자·Joker 여부는 중복 저장하지 않고 `tile_id`를 기존 `TileCatalog`로 복원한다.

## 초기 상태 불변식

| 참가자 | Rack 합계 | Pool | 전체 |
|---:|---:|---:|---:|
| 2명 | 28 | 78 | 106 |
| 3명 | 42 | 64 | 106 |
| 4명 | 56 | 50 | 106 |

공통:

- 참가자별 Rack 14개
- Tile ID 중복 0
- Tile 유실 0
- 현재 턴 사용자는 GamePlayer 중 1명
- 선 플레이어 Seat는 1..playerCount
- Table Meld는 빈 배열

## REST 계약

### GET /api/games/{gameId}

GamePlayer만 조회 가능하다. 응답은 `GamePrivateState`이며 상대 Rack은 Count만 포함한다.

### GET /api/me/active-game

```json
{
  "active": true,
  "gameId": 1,
  "roomId": 1,
  "status": "IN_PROGRESS"
}
```

활성 게임이 없으면 ID와 Status는 null이다.

## 에러 계약

- `GAME_NOT_FOUND`
- `GAME_MEMBERSHIP_REQUIRED`
- 기존 `ROOM_ALREADY_PLAYING`, `ROOM_OWNER_REQUIRED`, `ROOM_PLAYERS_NOT_READY` 유지

BLOCKED·DELETED 사용자는 REST와 STOMP 보호 경계에서 차단한다.

## MySQL 8.4 직접 확인 SQL

```sql
SELECT * FROM games ORDER BY id DESC LIMIT 1;
SELECT * FROM game_players WHERE game_id = ? ORDER BY seat_order;
SELECT location, COUNT(*)
FROM game_tiles
WHERE game_id = ?
GROUP BY location;

SELECT gp.user_id, COUNT(*) AS rack_count
FROM game_tiles gt
JOIN game_players gp ON gp.id = gt.owner_game_player_id
WHERE gt.game_id = ?
  AND gt.location = 'RACK'
GROUP BY gp.user_id;

SELECT COUNT(*) AS total_tiles, COUNT(DISTINCT tile_id) AS distinct_tiles
FROM game_tiles
WHERE game_id = ?;
```
