# Phase 7 Direct Verification Guide

## 자동 검증

```powershell
cd backend
.\mvnw.cmd clean test

cd ..\frontend
npm run check
```

기대 결과:

- Backend: 290 tests, failures 0, errors 0, skipped 0
- Frontend: 21 files, 167 tests 통과
- Vue TypeScript type-check 통과
- Vite production build 통과

## MySQL 8.4 Migration

```powershell
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 backend
```

확인 항목:

- MySQL `8.4.10` healthy
- Flyway schema version `5`
- `game_melds`와 `game_tiles.game_meld_id` 생성
- Backend/Frontend healthy

현재 Flyway는 MySQL 8.4가 공식 검증 상한 8.1보다 새 버전이라는 경고를 출력하지만, 실제 V1~V5 validation과 migration은 성공했다.

## 브라우저 대표 시나리오

`Phase7_Runtime_Fixture_Guide.md`의 픽스처를 적용하고 다음을 확인한다.

- WebSocket `CONNECTED`
- RED 789 Hold Overlay가 3장으로 승격
- BLUE 123 Hold Overlay가 3장으로 승격
- Draft 합계가 정확히 `30/30`
- COMMIT 성공 후 Rack `14 -> 8`
- 확정 Table에 RUN 24점과 RUN 6점 표시
- 현재 사용자 `initialMeldCompleted=true`
- `gameVersion 0 -> 1`
- `turnNumber 1 -> 2`
- 다음 참가자 턴
- 새로고침 후 Table 유지
- 두 계정에서 같은 공개 Table과 각자 Rack만 표시
- 브라우저 console error/warning 없음

## DB 사후 확인

```sql
SELECT id, version, turn_number, current_turn_user_id
FROM games
WHERE id = :gameId;

SELECT position_order, meld_type, score
FROM game_melds
WHERE game_id = :gameId
ORDER BY position_order;

SELECT tile_id, location, owner_game_player_id, game_meld_id, position_order
FROM game_tiles
WHERE game_id = :gameId
ORDER BY location, owner_game_player_id, game_meld_id, position_order;
```

## 실패 경로

- 30점 미만 첫 등록 거부와 전체 롤백
- invalid RUN/GROUP 거부
- duplicate/foreign/POOL/TABLE tile 거부
- stale version 거부와 최신 상태 재조회
- 같은 actionId replay 시 재실행 없음
- 비참가자의 `/turn/commit` 전송 거부
- COMMIT과 DRAW 동시 경합에서 하나의 권위 변경만 성공

