# Phase 3 Changed Files

작성 기준: 2026-07-15

## 최종 요약

```text
수정·생성 파일: 77
삭제 파일: 0
pom.xml 변경: 없음
frontend/package-lock.json 변경: 없음
Phase 1 규칙 엔진 변경: 없음
games/game_players 변경: 없음
```

## Root / Documents

| 상대 경로 | 변경 이유 |
|---|---|
| `README.md` | 현재 구현 범위와 자동 검증 기준을 Phase 3로 갱신 |
| `docs/Phase3_Changed_Files.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |
| `docs/Phase3_Completion_Report.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |
| `docs/Phase3_Direct_Verification_Guide.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |
| `docs/Phase3_Room_Concurrency_And_DB_Decisions.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |
| `docs/Phase3_Test_Case_Traceability.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |
| `docs/Phase3_WebSocket_Authentication_And_Event_Flow.md` | Phase 3 구현·동시성·WebSocket·테스트·직접 검증 증거 문서 |

## Backend Main

| 상대 경로 | 변경 이유 |
|---|---|
| `backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java` | Room 도메인 오류 코드 추가 |
| `backend/src/main/java/com/realtimetilegame/room/application/RoomCommandService.java` | 방 Command·Query·시작 조건·이벤트 Application 경계 |
| `backend/src/main/java/com/realtimetilegame/room/application/RoomEventPublisher.java` | 방 Command·Query·시작 조건·이벤트 Application 경계 |
| `backend/src/main/java/com/realtimetilegame/room/application/RoomQueryService.java` | 방 Command·Query·시작 조건·이벤트 Application 경계 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/ActiveRoomView.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomDetail.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomPage.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomParticipantView.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomStartAccepted.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomStartEligibility.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomSummary.java` | REST·WebSocket이 공유하는 Application 조회 결과 모델 |
| `backend/src/main/java/com/realtimetilegame/room/domain/ReadyStatus.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/Room.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomGameMode.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomPlayer.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomPlayerRepository.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomRepository.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomStatus.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/domain/RoomSummaryView.java` | Room·Membership 영속 도메인과 상태·Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/room/event/AfterCommitRoomEventListener.java` | AFTER_COMMIT 로비·대기방 이벤트 발행 |
| `backend/src/main/java/com/realtimetilegame/room/event/RealtimeEvent.java` | AFTER_COMMIT 로비·대기방 이벤트 발행 |
| `backend/src/main/java/com/realtimetilegame/room/event/RoomEventEnvelope.java` | AFTER_COMMIT 로비·대기방 이벤트 발행 |
| `backend/src/main/java/com/realtimetilegame/room/event/SpringRoomEventPublisher.java` | AFTER_COMMIT 로비·대기방 이벤트 발행 |
| `backend/src/main/java/com/realtimetilegame/room/infrastructure/JpaRoomPlayerRepository.java` | JPA Repository, Lock Query, 목록 Projection 구현 |
| `backend/src/main/java/com/realtimetilegame/room/infrastructure/JpaRoomRepository.java` | JPA Repository, Lock Query, 목록 Projection 구현 |
| `backend/src/main/java/com/realtimetilegame/room/infrastructure/JpaRoomSummaryView.java` | JPA Repository, Lock Query, 목록 Projection 구현 |
| `backend/src/main/java/com/realtimetilegame/room/infrastructure/SpringDataRoomJpaRepository.java` | JPA Repository, Lock Query, 목록 Projection 구현 |
| `backend/src/main/java/com/realtimetilegame/room/infrastructure/SpringDataRoomPlayerJpaRepository.java` | JPA Repository, Lock Query, 목록 Projection 구현 |
| `backend/src/main/java/com/realtimetilegame/room/presentation/MyActiveRoomController.java` | Room REST와 STOMP Command 진입점 |
| `backend/src/main/java/com/realtimetilegame/room/presentation/RoomController.java` | Room REST와 STOMP Command 진입점 |
| `backend/src/main/java/com/realtimetilegame/room/presentation/RoomMessageController.java` | Room REST와 STOMP Command 진입점 |
| `backend/src/main/java/com/realtimetilegame/room/presentation/dto/CreateRoomRequest.java` | Room REST와 STOMP Command 진입점 |
| `backend/src/main/java/com/realtimetilegame/room/websocket/ActionReplayStore.java` | actionId 중복 처리와 Room Command/Reply 계약 |
| `backend/src/main/java/com/realtimetilegame/room/websocket/RoomCommandReply.java` | actionId 중복 처리와 Room Command/Reply 계약 |
| `backend/src/main/java/com/realtimetilegame/room/websocket/RoomReadyCommand.java` | actionId 중복 처리와 Room Command/Reply 계약 |
| `backend/src/main/java/com/realtimetilegame/room/websocket/RoomStartCommand.java` | actionId 중복 처리와 Room Command/Reply 계약 |
| `backend/src/main/java/com/realtimetilegame/user/domain/UserRepository.java` | Room Command용 User PESSIMISTIC_WRITE 조회 확장 |
| `backend/src/main/java/com/realtimetilegame/user/infrastructure/JpaUserRepository.java` | Room Command용 User PESSIMISTIC_WRITE 조회 확장 |
| `backend/src/main/java/com/realtimetilegame/user/infrastructure/SpringDataUserJpaRepository.java` | Room Command용 User PESSIMISTIC_WRITE 조회 확장 |
| `backend/src/main/java/com/realtimetilegame/websocket/auth/StompAuthenticationChannelInterceptor.java` | STOMP CONNECT 인증과 Destination/Membership 인가 |
| `backend/src/main/java/com/realtimetilegame/websocket/auth/StompDestinationAuthorizationInterceptor.java` | STOMP CONNECT 인증과 Destination/Membership 인가 |
| `backend/src/main/java/com/realtimetilegame/websocket/auth/StompPrincipal.java` | STOMP CONNECT 인증과 Destination/Membership 인가 |
| `backend/src/main/java/com/realtimetilegame/websocket/config/WebSocketConfiguration.java` | 기존 /ws에 인증·인가 ChannelInterceptor 등록 |

## Backend Migration

| 상대 경로 | 변경 이유 |
|---|---|
| `backend/src/main/resources/db/migration/V2__create_rooms_and_room_players.sql` | rooms와 room_players 신규 Migration |

## Backend Tests

| 상대 경로 | 변경 이유 |
|---|---|
| `backend/src/test/java/com/realtimetilegame/persistence/MigrationAndRepositoryIntegrationTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/ActionReplayStoreTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/RoomApiIntegrationTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/RoomConcurrencyIntegrationTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/RoomEventAfterCommitIntegrationTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/RoomMessageControllerTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/room/StompRoomSecurityIntegrationTest.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |
| `backend/src/test/java/com/realtimetilegame/support/DatabaseCleanup.java` | Phase 3 Room·동시성·STOMP·Migration 회귀 테스트 |

## Frontend Source

| 상대 경로 | 변경 이유 |
|---|---|
| `frontend/src/App.vue` | Phase 3 Navigation과 범위 표시 |
| `frontend/src/api/roomApi.ts` | Room REST API Client |
| `frontend/src/assets/main.css` | Lobby·Modal·Waiting Room 기존 디자인 언어 확장 |
| `frontend/src/components/room/RoomCreateModal.vue` | CLASSIC 공개방 생성 Modal |
| `frontend/src/realtime/authenticatedStompClient.ts` | 메모리 Access Token 기반 인증 STOMP Client |
| `frontend/src/router/index.ts` | lobby/room Route Guard와 active room 복구 |
| `frontend/src/stores/room.ts` | 로비·대기방 상태와 Command Single Flight |
| `frontend/src/types/room.ts` | Room REST·Event·Reply TypeScript 계약 |
| `frontend/src/views/LobbyView.vue` | 방 목록·생성·입장 Lobby 화면 |
| `frontend/src/views/LoginView.vue` | 로그인 후 active room 또는 lobby 이동 |
| `frontend/src/views/RegisterView.vue` | 비밀번호 입력 Placeholder 보완 |
| `frontend/src/views/WaitingRoomView.vue` | 참가자·준비·시작 조건 대기방 화면 |

## Frontend Tests

| 상대 경로 | 변경 이유 |
|---|---|
| `frontend/src/__tests__/App.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |
| `frontend/src/__tests__/AuthenticatedStompClient.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |
| `frontend/src/__tests__/AuthenticationViews.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |
| `frontend/src/__tests__/RoomStore.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |
| `frontend/src/__tests__/RoomViews.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |
| `frontend/src/__tests__/RouterAuthenticationGuard.spec.ts` | Frontend Room·STOMP·Route·UI 회귀 테스트 |

## Patch 제외 확인

```text
.env
.env.local
backend/target
frontend/node_modules
frontend/dist
*.tsbuildinfo
IDE 설정
실제 JWT·Cookie
DB Dump
로컬 로그
```

위 항목은 Patch ZIP에 포함하지 않는다.
