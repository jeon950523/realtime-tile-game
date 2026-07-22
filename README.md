# Realtime Tile Game — Phase 4 Minimum Game Session

2~4인 실시간 숫자 타일 보드게임 프로젝트입니다. 연결 기반, 순수 규칙 엔진, 인증·프로필, CLASSIC 로비·대기방을 거쳐 최소 게임 세션과 최초 타일 분배까지 구현했습니다.

## 현재 구현 범위

### Phase 0 기반

- Spring Boot REST·MySQL Health
- CORS와 공통 오류 계약
- 공개 WebSocket/STOMP Health
- Vue 3/Vite/Pinia/Router/Axios/STOMP 기반

### Phase 1 순수 규칙 엔진

- 표준 타일 106개와 2~4인 초기 분배
- RUN·GROUP·첫 등록·재조합·조커·타일 무결성 검증
- CLASSIC·SPEED 순수 정책과 종료·점수 계산
- Spring/JPA/WebSocket과 분리된 순수 Java 도메인

### Phase 2 인증·프로필

- 회원가입·로그인·BCrypt
- JWT Access Token과 HttpOnly Refresh Cookie
- Refresh Token hash 저장·회전·재사용 차단
- 보호 REST의 현재 ACTIVE 사용자 공통 검증
- 프로필 조회·닉네임·아바타 수정
- Frontend 메모리 Access Token, 401 Single Flight, 새로고침 복구

### Phase 3 로비·대기방

- 2·3·4인 공개 CLASSIC 방 생성
- 방 목록·빠른 입장 후보·방 상세·현재 대기방 REST
- 한 사용자 활성 방 1개 제한
- 정원과 좌석 경쟁의 비관적 잠금
- 가장 작은 빈 `seatOrder` 재사용
- 방 나가기·마지막 이탈 CLOSED·방장 자동 위임
- 참가자 준비 상태와 시작 가능 조건 계산
- 시작 요청 성공 시 조건 승인만 반환하며 Game은 생성하지 않음
- Commit 이후 로비·대기방 이벤트 발행
- STOMP CONNECT JWT 인증
- 익명 Health 채널 유지
- Lobby 채널 인증, Room 채널 Membership 인가
- 현재 DB 사용자 상태와 JWT 만료를 SEND/SUBSCRIBE마다 재검증
- `actionId` 기반 READY·START 중복 처리
- LobbyView, RoomCreateModal, WaitingRoomView
- 새로고침 후 기존 대기방 복구
- 회원가입 비밀번호 Placeholder 보완

### Phase 4 최소 게임 세션

- 방장 START 시 Game·GamePlayer·GameTile 원자적 생성
- 표준 타일 106개 서버 셔플과 참가자당 14개 분배
- 선 플레이어 결정과 Room `PLAYING` 전환
- Commit 이후 GAME_STARTED·ROOM_REMOVED·개인 Game State 전송
- 공개 상태와 본인 Rack을 분리한 REST·WebSocket 응답
- `/games/:gameId` 화면과 새로고침 후 Active Game 복구

## 아직 구현하지 않은 범위

- 타일 Draw·Play·Meld Commit
- 테이블 재조합과 조커 교체
- 턴 종료·타이머
- 게임 종료·승자·점수 처리
- SPEED 방
- 비밀번호 방·강퇴·채팅·관전자
- Redis·다중 서버 브로커
- 서버 재시작 후 actionId 복구와 장기 방치방 자동 정리

## 주요 구조

```text
Realtime_Tile_Game/
├─ backend/src/main/java/com/realtimetilegame/
│  ├─ auth/
│  ├─ user/
│  ├─ security/
│  ├─ room/
│  ├─ websocket/
│  └─ game/domain/
├─ backend/src/main/resources/db/migration/
│  ├─ V1__create_users_and_refresh_tokens.sql
│  └─ V2__create_rooms_and_room_players.sql
├─ frontend/src/
│  ├─ api/
│  ├─ components/room/
│  ├─ realtime/
│  ├─ stores/
│  ├─ router/
│  ├─ types/
│  └─ views/
└─ docs/
   ├─ specs/
   ├─ Phase0_*.md
   ├─ Phase1_*.md
   ├─ Phase2_*.md
   └─ Phase3_*.md
```

## 자동 검증

Backend:

```powershell
cd .\backend
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd .\frontend
npm ci
npm run check
```

현재 저장소 문서에서 확인된 자동 검증 결과:

```text
Backend: Phase 4 환경에서는 Maven 의존성 다운로드 실패로 전체 실행 미확정
Frontend Vitest: 77개 통과
Frontend TypeScript: 통과
Frontend Production Build: 통과
npm ci: 성공 / 취약점 0건
```

세부 검증 근거는 [`docs/Phase4_Completion_Report.md`](docs/Phase4_Completion_Report.md)를 참고합니다.

## 로컬 실행 방법

### 1. 환경 파일과 MySQL 준비

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
docker compose ps
```

`.env`의 MySQL 비밀번호와 JWT Secret을 변경합니다. Compose MySQL은 호스트의 `33307` 포트를 사용하므로 `DB_URL`도 같은 포트를 가리키도록 맞춥니다.

### 2. 백엔드 실행

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 3. 프론트엔드 실행

```powershell
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://localhost:5173`에 접속합니다.

## 사용 방법

1. Chrome 일반 창과 시크릿 창에서 서로 다른 계정으로 회원가입·로그인합니다.
2. 첫 번째 사용자가 2인 CLASSIC 방을 만들고 두 번째 사용자가 입장합니다.
3. 두 사용자 모두 READY 상태로 변경합니다.
4. 방장이 START를 누르면 같은 gameId의 게임 화면으로 이동합니다.
5. 각 사용자에게 본인 Rack 14개만 상세 공개되고 상대방 타일은 개수만 표시되는지 확인합니다.
6. 새로고침한 뒤에도 같은 게임과 Rack이 복구되는지 확인합니다.

전체 검증 절차는 [`docs/Phase4_Direct_Verification_Guide.md`](docs/Phase4_Direct_Verification_Guide.md)를 따릅니다.

## 커밋 이력

| 순서 | 커밋 | 변경한 내용 |
| ---: | --- | --- |
| 1 | `a805bd4` Phase 4 완료 | Phase 0~4의 전체 소스를 한 번에 등록했습니다. REST·WebSocket 연결, 규칙 엔진, 인증, 로비·대기방, Game 세션 생성과 최초 분배가 이 커밋에 함께 포함됩니다. |

현재 저장소에는 커밋이 1개뿐이므로 첫 번째에서 두 번째 커밋으로의 변화는 설명할 수 없습니다. 앞으로는 Phase나 기능 단위로 커밋을 나누면 설계 변화와 문제 해결 과정을 Git 이력만으로 추적할 수 있습니다.

## 성능 개선 기록

테스트 통과 수는 기능 검증 수치이지 성능 수치가 아닙니다. 현재 이력에는 WebSocket 지연, 동시 입장 처리량, 게임 시작 시간의 전후 벤치마크가 없어 정량적인 성능 개선율을 주장하지 않습니다.

향후에는 2·3·4인 게임 시작을 각각 반복해 p50/p95 시작 시간, DB 쿼리 수, GAME_STARTED 전달 지연을 기록할 계획입니다.

## 현재 판정

```text
Phase 4 Frontend 자동 검증: 통과
사용자 Java 17 Backend clean test: 확인 필요
실제 MySQL 8.4 V3 Migration: 확인 필요
Chrome 일반 창·시크릿 창 2계정 실시간 검증: 확인 필요
Phase 4 최종 완료: 사용자 직접 검증 전 보류
```

직접 검증 순서는 `docs/Phase4_Direct_Verification_Guide.md`를 따릅니다.
