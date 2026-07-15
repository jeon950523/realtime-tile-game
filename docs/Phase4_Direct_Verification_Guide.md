# Phase 4 사용자 직접 검증 가이드

작성 기준: 2026-07-15 KST

## 1. 사전 조건

- Java 17
- MySQL 8.4
- Node 버전은 `frontend/package.json` engines 충족
- Chrome 일반 창과 시크릿 창
- Phase 3 최신 전체본에 이번 Patch만 적용
- 과거 Phase 3/Phase 4 Patch 중복 적용 금지

## 2. 자동 테스트

```powershell
cd .\backend
.\mvnw.cmd clean test
```

확인:

- 기존 221개 감소 없음
- 신규 Phase 4 테스트 포함
- Failures 0
- Errors 0
- Skipped 0
- BUILD SUCCESS
- Java 17로 실행

```powershell
cd ..\frontend
npm ci
npm run check
```

확인:

- TypeScript 통과
- Vitest 77개 이상 통과
- Production Build 통과

## 3. DB Migration

애플리케이션 시작 후 Flyway V3 성공을 확인한다.

```sql
SHOW TABLES LIKE 'games';
SHOW TABLES LIKE 'game_players';
SHOW TABLES LIKE 'game_tiles';
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

V1·V2 Checksum 오류가 없어야 한다.

## 4. 2계정 시작 흐름

1. Chrome 일반 창 A 로그인
2. 시크릿 창 B 로그인
3. A가 2인 CLASSIC 방 생성
4. B 입장
5. A·B READY
6. A가 START 한 번 클릭

기대:

- A·B 모두 같은 `/games/{gameId}`로 이동
- 방장 개인 Reply만 기다리지 않고 둘 다 이동
- Room Status PLAYING
- Game ID 동일
- 현재 턴 참가자 정확히 1명
- Pool 78
- 각 사용자 본인 Rack 14개
- 상대는 Rack 14개 수량만 표시
- Network 응답·Pinia Store·DOM에 상대 Tile ID/색상/숫자 0건
- Table Meld 0
- 조작 버튼 없음

## 5. DB 정합성

`?`를 실제 gameId로 바꾼다.

```sql
SELECT id, room_id, game_mode, status, current_turn_user_id,
       current_turn_seat_order, turn_number, finished_at, version
FROM games
WHERE id = ?;

SELECT id, game_id, user_id, seat_order, initial_meld_completed
FROM game_players
WHERE game_id = ?
ORDER BY seat_order;

SELECT location, COUNT(*) AS tile_count
FROM game_tiles
WHERE game_id = ?
GROUP BY location;

SELECT gp.user_id, COUNT(*) AS rack_count
FROM game_tiles gt
JOIN game_players gp ON gp.id = gt.owner_game_player_id
WHERE gt.game_id = ? AND gt.location = 'RACK'
GROUP BY gp.user_id;

SELECT COUNT(*) AS total_count, COUNT(DISTINCT tile_id) AS distinct_count
FROM game_tiles
WHERE game_id = ?;
```

2인 기대:

```text
games 1
game_players 2
game_tiles 106
RACK 28
POOL 78
사용자별 Rack 14
total_count 106
distinct_count 106
```

3인·4인도 각각 Pool 64·50을 추가 확인한다.

## 6. 새로고침 복구

A와 B GameView에서 각각 F5:

- Refresh Token reissue 정상
- `GET /api/me/active-game`으로 같은 gameId 복구
- `GET /api/games/{gameId}`으로 같은 Private State 복구
- 내 Rack Tile ID와 positionOrder 동일
- 현재 턴 동일
- Pool Count 동일
- Game Topic 활성 구독 1개
- 개인 Game State Queue 활성 구독 1개
- Browser Console Error 0

## 7. 보안 확인

STOMP Test Client 또는 개발용 Console에서 인증 참가자가 다음 전송을 시도한다.

```text
SEND /topic/games/{gameId}
SEND /user/queue/game-state
```

기대:

- 안전한 FORBIDDEN Error
- 다른 참가자 수신 0건

비회원 계정으로:

```text
GET /api/games/{gameId}
SUBSCRIBE /topic/games/{gameId}
```

기대:

- REST `GAME_MEMBERSHIP_REQUIRED` 403
- STOMP `GAME_MEMBERSHIP_REQUIRED`

## 8. 중복·동시 시작

### 동일 actionId 재전송

- 최초 START 성공 Reply의 gameId 기록
- 같은 actionId로 재전송
- `DUPLICATE_ACTION_REPLAYED`
- payload gameId 동일
- DB Game 1개
- GAME_STARTED 공개 Event 1회

### 서로 다른 actionId 동시 전송

- 가능한 한 동시에 두 START 전송
- 성공 1건
- 나머지 `ROOM_ALREADY_PLAYING`
- Game 1개, GameTile 106개

## 9. 회귀

- 회원가입·로그인·프로필·로그아웃
- Refresh Rotation
- Health REST/MySQL/WebSocket
- 로비 목록
- 방 생성·입장·READY
- 방장 위임
- 마지막 이탈 CLOSED
- 대기방 새로고침 복구
- Browser Console Error 0

## 10. 최종 완료 판정

자동 Backend 전체 테스트와 위 사용자 검증이 모두 끝나기 전에는 Phase 4를 최종 완료 처리하지 않는다.
