# Phase 5 Direct Verification Guide

작성 기준: 2026-07-15 KST

## 1. 사전 조건

- 기준 전체본 `phase0715-22-09-phase4-final-clean-source.zip`
- 이번 Phase 5 Patch만 적용
- Java 17, MySQL 8.4
- Node는 `frontend/package.json` engines 충족
- Chrome 일반 창 A와 시크릿 창 B
- 과거 Phase 4 Patch/Fix Patch 재적용 금지

## 2. 자동 검증

```powershell
cd .\backend
.\mvnw.cmd clean test

cd ..\frontend
npm ci
npm run check
```

실제 결과에서 다음을 확인한다.

```text
Backend Tests run / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS
Frontend Vitest 전체 통과
TypeScript 통과
Production Build 통과
```

## 3. MySQL V4

애플리케이션 시작 후:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SHOW COLUMNS FROM games LIKE 'current_turn_id';
SHOW COLUMNS FROM games LIKE 'current_turn_started_at';
SHOW COLUMNS FROM games LIKE 'current_turn_deadline_at';
SHOW COLUMNS FROM games LIKE 'consecutive_pass_count';
```

기존 Phase 4 Game Row가 있는 DB에서도 V4가 성공해야 한다.

## 4. 2계정 첫 Draw

1. A·B 로그인
2. 2인 CLASSIC 방 생성·입장·READY·START
3. 현재 턴 계정 확인
4. 현재 턴 계정에서 Draw 한 번

기대:

```text
현재 사용자 Rack 14 → 15
Pool 78 → 77
gameVersion 0 → 1
turnNumber 1 → 2
다음 실제 Seat로 현재 턴 이동
새 currentTurnId
새 turnDeadlineAt
consecutivePassCount 0
```

Privacy:

- Draw한 계정만 새 Tile ID·색·숫자를 본다.
- 상대 계정은 Draw 사용자 Rack Count 15만 본다.
- `/topic/games/{gameId}` Payload에 `tileId`, `color`, `number`, `joker`가 없어야 한다.
- 상대의 `/user/queue/game-state`에는 상대 자신의 Rack만 있어야 한다.

## 5. 두 번째 Draw·순환

다음 턴 계정이 Draw한다.

```text
두 번째 사용자 Rack 14 → 15
Pool 77 → 76
gameVersion 1 → 2
turnNumber 2 → 3
2인에서는 첫 사용자로 턴 복귀
```

3인 방에서도 Seat 1→2→3→1 순환을 확인한다.

## 6. 비현재 사용자·Stale Version

- 비현재 사용자 Draw 버튼은 비활성이다.
- 강제 SEND 시 `NOT_CURRENT_TURN`을 확인한다.
- 이전 `gameVersion`으로 강제 SEND 시 `STALE_GAME_VERSION`을 확인한다.
- 실패 후 Rack·Pool·Turn·Version이 변하지 않아야 한다.

## 7. PASS

일반 브라우저 상태에서는 Pool이 남아 있으므로:

```text
PASS 버튼 비활성
강제 SEND → PASS_NOT_ALLOWED
```

Pool 0 PASS 성공은 자동 테스트 Fixture로 검증한다. 수동 DB 조작을 운영 DB에서 수행하지 않는다.

## 8. Replay

동일한 UUID actionId와 동일 Version으로 Draw를 거의 동시에 두 번 보낸다.

기대:

```text
Tile 이동 1회
Pool -1만 발생
Turn 전환 1회
두 Reply의 committed gameVersion 동일
두 번째 Reply duplicate = true
```

서로 다른 actionId와 같은 Version 경쟁:

```text
성공 1회
나머지 STALE_GAME_VERSION
```

## 9. F5·재로그인 복구

A·B 모두 F5:

- 같은 gameId
- 최신 gameVersion
- 최신 currentTurnId·currentTurnUser·turnNumber
- 최신 deadline
- 최신 Pool Count
- 각자 자기 Rack 상세만 복구
- Game Topic 1개
- Private Queue 1개
- Reply Queue 1개

로그아웃 후 Game은 `IN_PROGRESS`로 유지되고 재로그인 시 Active Game으로 복구돼야 한다.

## 10. Countdown

- 서버 `turnDeadlineAt` 기준 표시
- 0 이하에서 `0초` Clamp
- 0초가 되어도 자동 Draw/PASS 요청 0건

## 11. DB 정합성

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

SELECT COUNT(*) AS total_count,
       COUNT(DISTINCT tile_id) AS distinct_count
FROM game_tiles
WHERE game_id = ?;
```

첫 Draw 후 2인 기대:

```text
RACK 29
POOL 77
total 106
distinct 106
```

## 12. 회귀

- 회원가입·로그인·Refresh·프로필·로그아웃
- Health REST / MySQL / WebSocket
- 방 생성·입장·READY
- 방장 위임
- 마지막 대기방 이탈 CLOSED
- Phase 4 Game Start
- 2·3·4인 초기 분배
- 상대 Rack Privacy
- Active Game 복구
- Browser Console Error 0
