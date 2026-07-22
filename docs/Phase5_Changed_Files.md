# Phase 5 Changed Files

작성 기준: 2026-07-15 KST

기준 전체본:

```text
phase0715-22-09-phase4-final-clean-source.zip
```

## 요약

```text
코드 수정 28개
코드 신규 17개
코드 삭제 0개
문서 수정 3개
문서 신규 6개 — 이 문서 포함
```

코드 Patch ZIP에는 아래 Backend·Frontend 수정/신규 파일만 포함한다. 문서는 별도 문서 ZIP에만 포함한다.

## 코드 수정 파일

- `backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameEventPublisher.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameStartService.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameStateAssembler.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GamePublicState.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/Game.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTile.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTileRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/event/SpringGameEventPublisher.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameTileRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameJpaRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameTileJpaRepository.java`
- `backend/src/main/java/com/realtimetilegame/websocket/auth/StompDestinationAuthorizationInterceptor.java`
- `backend/src/test/java/com/realtimetilegame/game/AfterCommitGameStartedEventListenerTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GamePersistenceConstraintIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameStartIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/RoomGameStartTransitionTest.java`
- `backend/src/test/java/com/realtimetilegame/room/StompRoomSecurityIntegrationTest.java`
- `frontend/src/__tests__/AuthenticatedStompClient.spec.ts`
- `frontend/src/__tests__/GameApi.spec.ts`
- `frontend/src/__tests__/GameStore.spec.ts`
- `frontend/src/__tests__/GameView.spec.ts`
- `frontend/src/realtime/authenticatedStompClient.ts`
- `frontend/src/stores/game.ts`
- `frontend/src/types/game.ts`
- `frontend/src/views/GameView.vue`

## 코드 신규 파일

- `backend/src/main/java/com/realtimetilegame/game/application/GameTurnCommandService.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GameTurnCommand.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GameTurnCommandResult.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/TileDrawnPayload.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/TurnPassedPayload.java`
- `backend/src/main/java/com/realtimetilegame/game/event/AfterCommitGameTurnEventListener.java`
- `backend/src/main/java/com/realtimetilegame/game/event/GameTurnCommittedEvent.java`
- `backend/src/main/java/com/realtimetilegame/game/presentation/GameMessageController.java`
- `backend/src/main/java/com/realtimetilegame/game/websocket/GameActionReplayStore.java`
- `backend/src/main/java/com/realtimetilegame/game/websocket/GameCommandReply.java`
- `backend/src/main/resources/db/migration/V4__add_game_turn_runtime_columns.sql`
- `backend/src/test/java/com/realtimetilegame/game/AfterCommitGameTurnEventListenerTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameActionReplayStoreTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameMessageControllerTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameTurnCommandIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/domain/session/GameTurnDomainTest.java`
- `backend/src/test/java/com/realtimetilegame/persistence/GameV4MigrationIntegrationTest.java`

## 코드 삭제 파일

- 없음

## 문서 수정 파일

- `docs/Phase4_Completion_Report.md`
- `docs/Phase4_Direct_Verification_Guide.md`
- `docs/Phase4_Test_Case_Traceability.md`

## 문서 신규 파일

- `docs/Phase5_Architecture_And_Transaction_Decisions.md`
- `docs/Phase5_Changed_Files.md`
- `docs/Phase5_Completion_Report.md`
- `docs/Phase5_Database_And_Turn_State_Contract.md`
- `docs/Phase5_Direct_Verification_Guide.md`
- `docs/Phase5_Test_Case_Traceability.md`

## Patch 제외 확인

```text
.env
.env.local
target
node_modules
dist
coverage
비밀값
실행 로그
```

위 항목은 코드 Patch ZIP과 문서 ZIP에 포함하지 않는다.
