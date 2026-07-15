# Realtime Tile Game — Phase 3 Lobby And Waiting Room Foundation

2~4인 실시간 숫자 타일 보드게임 프로젝트다. Phase 0 연결 기반, Phase 1 순수 규칙 엔진, Phase 2 인증·프로필 위에 CLASSIC 로비와 대기방 기반을 구현했다.

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

## Phase 3에서 구현하지 않은 범위

- `games`, `game_players`
- 실제 GameState와 GameSession
- 타일 셔플·분배와 선 플레이어 결정
- `RoomStatus.PLAYING` 전환
- 가짜 `gameId` 또는 `GAME_STARTED` 이벤트
- 게임 화면과 턴 처리
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

현재 자동 검증 결과:

```text
Backend: 195개 통과 / 실패 0 / 오류 0 / 스킵 0 / BUILD SUCCESS
Frontend Vitest: 56개 통과
Frontend TypeScript: 통과
Frontend Production Build: 통과
npm ci: 성공 / 취약점 0건
```

검수 환경에서는 Java 21 JVM에서 Java 17 release 대상으로 컴파일했다. H2 MySQL Mode에서 V1·V2 Migration과 `ddl-auto=validate`를 확인했다.

## 현재 판정

```text
Phase 3 정적·자동 검증: 통과
사용자 Java 17 단일 clean test: 확인 필요
실제 MySQL 8.4 V2 Migration: 확인 필요
Chrome 일반 창·시크릿 창 2계정 실시간 검증: 확인 필요
Phase 4: 사용자 직접 검증 전 보류
```

직접 검증 순서는 `docs/Phase3_Direct_Verification_Guide.md`를 따른다.
