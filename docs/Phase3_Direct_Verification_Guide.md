# Phase 3 Direct Verification Guide

## 1. 검증 전제

```text
Java 17
Node v22 LTS
Docker Desktop
MySQL 8.4
Chrome 일반 창
Chrome 시크릿 창
서로 다른 계정 2개 이상
```

이번 자동 검수 환경에서는 실제 Docker와 Chrome을 실행하지 못했다. 아래 절차는 사용자 환경에서 직접 확인한다.

## 2. Patch 적용

기준 전체본:

```text
phase0715-11-04-phase2-final-game.zip
```

Patch ZIP을 프로젝트 루트에 상대 경로 그대로 덮어쓴다.

## 3. Backend 자동 테스트

```powershell
cd .\backend
.\mvnw.cmd clean test
```

기대 결과:

```text
Tests run: 195
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 4. Frontend 자동 테스트

```powershell
cd .\frontend
npm ci
npm run check
```

기대 결과:

```text
Vitest: 56
TypeScript: 통과
Production Build: 통과
```

## 5. MySQL 8.4 V2 Migration

학원 PC 포트 기준:

```text
Host 33307 → Container 3306
```

```powershell
docker compose up -d mysql
docker compose ps
```

Backend 환경변수 예시:

```powershell
$env:DB_URL="jdbc:mysql://localhost:33307/realtime_tile_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
$env:DB_USERNAME="rtg_user"
$env:DB_PASSWORD="<local-secret>"
$env:JWT_ACCESS_SECRET_BASE64="<local-generated-secret>"
$env:AUTH_REFRESH_COOKIE_SECURE="false"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
.\mvnw.cmd spring-boot:run
```

실제 Secret은 문서, Screenshot, 채팅에 남기지 않는다.

HeidiSQL 확인:

```text
flyway_schema_history: V1, V2
rooms
room_players
```

Entity Validation 오류 없이 Backend가 시작되는지 확인한다.

## 6. Frontend 실행

```powershell
cd .\frontend
npm run dev
```

## 7. Chrome 2계정 시나리오

### A. 실시간 방 생성

1. 일반 창 계정 A 로그인
2. 시크릿 창 계정 B 로그인
3. 두 계정 모두 `/lobby` 진입
4. A가 2인 CLASSIC 방 생성
5. A는 `/rooms/{roomId}`로 이동
6. B 로비에 새 방이 새로고침 없이 표시되는지 확인

### B. 입장과 정원

1. B가 방 입장
2. 양쪽 대기방에 A·B가 즉시 표시되는지 확인
3. `seatOrder`가 1·2인지 확인
4. 로비의 인원이 `1/2 → 2/2`로 갱신되는지 확인
5. 추가 계정의 입장은 `ROOM_FULL`인지 확인

### C. 준비 동기화

1. A 준비
2. B 준비
3. 양쪽 화면에서 READY 상태가 한 번씩 반영되는지 확인
4. 방장도 READY가 필요한지 확인
5. 전원 준비 후 방장 시작 버튼이 활성화되는지 확인

### D. 시작 경계

1. 비방장 B의 Start 요청은 `ROOM_OWNER_REQUIRED`
2. 방장 A 요청은 `ROOM_START_REQUEST_ACCEPTED`
3. 화면에 조건 충족 메시지 표시
4. 대기방에 그대로 머무름
5. DB에 `games`, `game_players`가 생기지 않음
6. Room 상태가 `WAITING` 유지
7. 가짜 gameId가 없음

### E. 방장 위임

1. 3인 방에 A, B, C 입장
2. A가 방 나가기
3. 먼저 입장한 B가 방장인지 확인
4. 모든 화면에서 방장 표시가 즉시 바뀌는지 확인
5. 기존 준비 상태가 유지되는지 확인

### F. 마지막 참가자 이탈

1. 참가자를 순서대로 나가게 함
2. 마지막 참가자 이탈 후 Room이 `CLOSED`
3. 로비에서 해당 방이 즉시 제거되는지 확인

### G. 새로고침 복구

1. 대기방에서 F5
2. `/api/auth/reissue`가 중복되지 않고 1회인지 확인
3. `GET /api/me/active-room` 확인
4. 같은 `/rooms/{roomId}`로 복구되는지 확인
5. Room Topic 구독이 1개인지 확인

### H. 마지막 자리 경쟁

Postman Runner 또는 추가 브라우저로 같은 마지막 자리에 두 계정이 동시에 `POST /join`한다.

```text
1명 성공
1명 ROOM_FULL
최종 인원 maxPlayers와 동일
좌석 중복 없음
```

### I. BLOCKED 사용자

1. 로그인·Room 연결 유지
2. DB에서 해당 User를 `BLOCKED`로 변경
3. READY 전송
4. 보호 메시지가 거부되는지 확인
5. JWT를 Console이나 서버 로그에 출력하지 않는지 확인

## 8. 개발자 도구

### Network

```text
GET /api/rooms
GET /api/rooms/quick-match
POST /api/rooms
GET /api/rooms/{id}
POST /api/rooms/{id}/join
DELETE /api/rooms/{id}/members/me
GET /api/me/active-room
/ws
```

### WebSocket Frames

```text
CONNECT Authorization Header 존재
WebSocket URL Query Token 없음
Lobby Subscription 1개
Room Subscription 1개
READY Event 1회
중복 Reply 외 공개 Event 중복 없음
```

### Storage

```text
localStorage Access Token 없음
sessionStorage Access Token 없음
Refresh Token JavaScript 접근 불가
```

### Console

```text
Error 0
Unhandled Promise Rejection 0
Vue Warning 0
JWT 문자열 출력 0
```

## 9. Phase 4 진행 조건

다음이 모두 확인될 때 Phase 3를 최종 완료 처리한다.

```text
Java 17 Backend 195개
Frontend 56개
MySQL V2 적용
2계정 방 생성·입장·준비
방장 위임
마지막 이탈 종료
새로고침 복구
Health REST·MySQL·WebSocket 회귀
Console Error 0
```
