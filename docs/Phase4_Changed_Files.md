# Phase 4 변경 파일 목록
작성 기준: 2026-07-15 KST
## 집계
- 신규 파일: **50개**
- 수정 파일: **23개**
- 삭제 파일: **1개**
- 수정·생성 파일 합계: **73개**
- 코드 Patch ZIP은 수정·생성 파일을 경로 그대로 포함하고, 삭제 파일은 `DELETE_FILES.txt`로 명시한다.

## 신규 파일
- `backend/src/main/java/com/realtimetilegame/game/application/GameEventPublisher.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameQueryService.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameStartRandomizer.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameStartService.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameStateAssembler.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/ActiveGameView.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GamePlayerPublicView.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GamePrivateState.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GamePublicState.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GameRackTileView.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GameStartResult.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/GameStartedPayload.java`
- `backend/src/main/java/com/realtimetilegame/game/config/GameSessionConfiguration.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/Game.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GamePlayer.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GamePlayerRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameStatus.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTile.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTileLocation.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTileRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/event/AfterCommitGameStartedEventListener.java`
- `backend/src/main/java/com/realtimetilegame/game/event/GameStartedCommittedEvent.java`
- `backend/src/main/java/com/realtimetilegame/game/event/SpringGameEventPublisher.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/random/SecureGameStartRandomizer.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGamePlayerRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameTileRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameJpaRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGamePlayerJpaRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameTileJpaRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/presentation/GameController.java`
- `backend/src/main/java/com/realtimetilegame/game/presentation/MyActiveGameController.java`
- `backend/src/main/resources/db/migration/V3__create_games_game_players_and_game_tiles.sql`
- `backend/src/test/java/com/realtimetilegame/game/AfterCommitGameStartedEventListenerTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameApiIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameEventAtomicityIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameInitialDealTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GamePersistenceConstraintIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameStartConcurrencyIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameStartDeterministicRandomizerIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameStartIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/game/RoomGameStartTransitionTest.java`
- `frontend/src/__tests__/GameApi.spec.ts`
- `frontend/src/__tests__/GameStore.spec.ts`
- `frontend/src/__tests__/GameView.spec.ts`
- `frontend/src/api/gameApi.ts`
- `frontend/src/stores/game.ts`
- `frontend/src/types/game.ts`
- `frontend/src/views/GameView.vue`

## 수정 파일
- `backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java`
- `backend/src/main/java/com/realtimetilegame/room/application/RoomCommandService.java`
- `backend/src/main/java/com/realtimetilegame/room/domain/Room.java`
- `backend/src/main/java/com/realtimetilegame/room/presentation/RoomMessageController.java`
- `backend/src/main/java/com/realtimetilegame/room/websocket/RoomCommandReply.java`
- `backend/src/main/java/com/realtimetilegame/websocket/auth/StompDestinationAuthorizationInterceptor.java`
- `backend/src/test/java/com/realtimetilegame/persistence/MigrationAndRepositoryIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/room/RoomApiIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/room/RoomMessageControllerTest.java`
- `backend/src/test/java/com/realtimetilegame/room/StompBrokerForgeryIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/room/StompRoomSecurityIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/support/DatabaseCleanup.java`
- `frontend/src/App.vue`
- `frontend/src/__tests__/App.spec.ts`
- `frontend/src/__tests__/AuthenticatedStompClient.spec.ts`
- `frontend/src/__tests__/RoomStore.spec.ts`
- `frontend/src/__tests__/RoomViews.spec.ts`
- `frontend/src/__tests__/RouterAuthenticationGuard.spec.ts`
- `frontend/src/assets/main.css`
- `frontend/src/realtime/authenticatedStompClient.ts`
- `frontend/src/router/index.ts`
- `frontend/src/stores/room.ts`
- `frontend/src/views/WaitingRoomView.vue`

## 삭제 파일
- `backend/src/main/java/com/realtimetilegame/room/application/dto/RoomStartAccepted.java`

## 범위 통제 확인
- Phase 1의 Tile·RUN·GROUP·Joker 순수 규칙 엔진 파일은 수정하지 않았다.
- Draw, Play Tile, Meld Commit, Table Rearrangement, Turn End, Game End, SPEED, Redis, 다중 서버는 추가하지 않았다.
- `.env`, 실제 비밀값, `target`, `node_modules`, `dist`, 로컬 로그는 Patch ZIP에서 제외한다.
