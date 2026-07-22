# Phase 7 Changed Files

## Backend production

```text
backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java
backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTurnCommand.java
backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTurnMeldCommand.java
backend/src/main/java/com/realtimetilegame/game/application/dto/GamePublicState.java
backend/src/main/java/com/realtimetilegame/game/application/dto/GameTableMeldView.java
backend/src/main/java/com/realtimetilegame/game/application/dto/GameTableTileView.java
backend/src/main/java/com/realtimetilegame/game/application/dto/MeldsCommittedPayload.java
backend/src/main/java/com/realtimetilegame/game/application/GameQueryService.java
backend/src/main/java/com/realtimetilegame/game/application/GameStartService.java
backend/src/main/java/com/realtimetilegame/game/application/GameStateAssembler.java
backend/src/main/java/com/realtimetilegame/game/application/GameTurnCommandService.java
backend/src/main/java/com/realtimetilegame/game/application/GameTurnCommitService.java
backend/src/main/java/com/realtimetilegame/game/application/GameTurnStateFactory.java
backend/src/main/java/com/realtimetilegame/game/application/RuleViolationMapper.java
backend/src/main/java/com/realtimetilegame/game/config/GameSessionConfiguration.java
backend/src/main/java/com/realtimetilegame/game/domain/session/Game.java
backend/src/main/java/com/realtimetilegame/game/domain/session/GameMeld.java
backend/src/main/java/com/realtimetilegame/game/domain/session/GameMeldRepository.java
backend/src/main/java/com/realtimetilegame/game/domain/session/GamePlayer.java
backend/src/main/java/com/realtimetilegame/game/domain/session/GameTile.java
backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameMeldRepository.java
backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameMeldJpaRepository.java
backend/src/main/java/com/realtimetilegame/game/infrastructure/session/SpringDataGameTileJpaRepository.java
backend/src/main/java/com/realtimetilegame/game/presentation/GameMessageController.java
backend/src/main/java/com/realtimetilegame/websocket/auth/StompDestinationAuthorizationInterceptor.java
backend/src/main/resources/db/migration/V5__add_persistent_table_melds.sql
```

## Backend tests

```text
backend/src/test/java/com/realtimetilegame/game/domain/session/GameTurnDomainTest.java
backend/src/test/java/com/realtimetilegame/game/GameMessageControllerTest.java
backend/src/test/java/com/realtimetilegame/game/GamePersistenceConstraintIntegrationTest.java
backend/src/test/java/com/realtimetilegame/game/GameTurnCommitIntegrationTest.java
backend/src/test/java/com/realtimetilegame/room/StompRoomSecurityIntegrationTest.java
backend/src/test/java/com/realtimetilegame/support/DatabaseCleanup.java
```

## Frontend production and tests

```text
frontend/src/__tests__/Phase7CommitFrontend.spec.ts
frontend/src/__tests__/RackGroupHold.spec.ts
frontend/src/__tests__/RackMotionPolish.spec.ts
frontend/src/__tests__/RackPresentation.spec.ts
frontend/src/__tests__/RackVisualGroups.spec.ts
frontend/src/__tests__/TurnDraft.spec.ts
frontend/src/components/game/CommittedTableBoard.vue
frontend/src/components/game/DraftMeld.vue
frontend/src/components/game/GameBoard.vue
frontend/src/components/game/TileRack.vue
frontend/src/components/game/TurnActionControl.vue
frontend/src/components/game/TurnDraftBoard.vue
frontend/src/composables/game/useRackPresentation.ts
frontend/src/composables/game/useTurnDraft.ts
frontend/src/domain/game/rackVisualGroups.ts
frontend/src/domain/game/turnDraftValidation.ts
frontend/src/realtime/authenticatedStompClient.ts
frontend/src/stores/game.ts
frontend/src/styles/game/rummikub-inspired.css
frontend/src/types/game.ts
frontend/src/types/turnDraft.ts
frontend/src/views/GameView.vue
```

## Runtime fixture and documents

```text
scripts/phase7/phase7_exact30_runtime_fixture.sql
README.md
docs/Phase7_Changed_Files.md
docs/Phase7_Completion_Report.md
docs/Phase7_Direct_Verification_Guide.md
docs/Phase7_Test_Case_Traceability.md
docs/Phase7_Database_And_Table_Meld_Contract.md
docs/Phase7_TurnDraft_And_Commit_Flow.md
docs/Phase7_Runtime_Fixture_Guide.md
docs/specs/Realtime_Tile_Game_Document_Index_v1.md
docs/specs/Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md
docs/specs/Realtime_Tile_Game_Test_Case_Matrix_v1.md
docs/specs/Realtime_Tile_Game_WebSocket_Message_Spec_v1.md
docs/specs/Realtime_Tile_Game_Server_GameState_Model_v1.md
docs/specs/Realtime_Tile_Game_ERD_Table_Spec_v1.md
docs/specs/Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md
```

`.env`, build output, dependency caches, MySQL data, 전체 소스 Snapshot은 산출물에 포함하지 않는다.

