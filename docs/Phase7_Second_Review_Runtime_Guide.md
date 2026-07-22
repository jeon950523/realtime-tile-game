# Phase 7 Second Review Runtime 검증 가이드와 결과

## 실행 명령

```powershell
docker compose up -d --build
docker compose ps
docker compose logs --tail 150 backend
docker compose logs --tail 100 frontend
docker compose logs --tail 100 mysql
curl.exe -sS -i http://127.0.0.1:8080/api/health
curl.exe -sS -i http://127.0.0.1:5173
```

`docker compose down -v`는 실행하지 않았고 MySQL volume과 사용자 데이터는 보존했다.

## 2026-07-18 실제 결과

- backend/frontend/mysql: 모두 healthy
- Flyway: 5 migrations validated, current schema version 5, no migration necessary
- Backend Health: HTTP 200, application UP, database UP
- Frontend: HTTP 200, 새 production asset 응답
- 재기동 직후 기존 Chrome 두 세션에서 `/api/games/2` GET 200 두 건과 WebSocket 2 connections 확인
- 별도 검증 브라우저: 로그인 화면 정상, Console warning/error 0

별도 검증 브라우저는 기존 Chrome 인증을 공유하지 않았다. 임의 로그인·Fixture·게임 생성은 금지되어 Game 화면의 1920×1080, 1600×900, 1366×768, 1280×720 수동 조합·Commit 시나리오는 실행하지 않았다. Windows Chrome 읽기 제어도 현재 URL을 안전하게 확정하지 못해 입력 전 중단됐다. 따라서 실제 Chrome에서 재조합·21~30장 Rack·Console/Network를 직접 통과했다고 주장하지 않는다.

## 사용자 수동 체크

1. 이미 로그인한 두 Chrome 창을 새로고침한다.
2. 내 턴에 Rack/Action 녹색 border와 `내 턴` badge를 확인한다.
3. 첫 등록 전 기존 Meld가 잠기고 새 Meld만 추가되는지 확인한다.
4. 첫 등록 완료 계정에서 기존 Meld 연장·분리·병합·Meld 간 이동 후 전체 Table Commit을 확인한다.
5. invalid intermediate에서 Commit이 비활성이고 Cancel이 baseline을 복원하는지 확인한다.
6. 21~30장 Rack을 1920×1080, 1600×900, 1366×768, 1280×720에서 확인한다.
7. DevTools Console error와 실패 Network 요청을 확인한다.

