# Phase 7 Second Review 변경 파일

기준 Working Tree: `phase0717-20-32-phase5_5C-final-clean-source`

## Backend 필수 구현

- `backend/src/main/java/com/realtimetilegame/game/application/GameTurnCommitService.java`
- `backend/src/main/java/com/realtimetilegame/game/application/GameTurnStateFactory.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTurnCommand.java`
- `backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTableMeldCommand.java` (신규)
- `backend/src/main/java/com/realtimetilegame/game/application/dto/MeldsCommittedPayload.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/rule/initial/InitialMeldValidator.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameMeld.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameMeldRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTile.java`
- `backend/src/main/java/com/realtimetilegame/game/infrastructure/session/JpaGameMeldRepository.java`
- `backend/src/main/java/com/realtimetilegame/game/presentation/GameMessageController.java`
- 삭제: `backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTurnMeldCommand.java`

## Backend 테스트

- `backend/src/test/java/com/realtimetilegame/game/GameMessageControllerTest.java`
- `backend/src/test/java/com/realtimetilegame/game/GameTurnCommitIntegrationTest.java`

## Frontend 필수 구현

- `frontend/src/types/turnDraft.ts`
- `frontend/src/types/game.ts`
- `frontend/src/domain/game/turnDraftValidation.ts`
- `frontend/src/domain/game/rackLayout.ts` (신규)
- `frontend/src/composables/game/useWorkingTable.ts` (신규)
- `frontend/src/composables/game/useTurnDraft.ts`
- `frontend/src/components/game/DraftMeld.vue`
- `frontend/src/components/game/WorkingTableBoard.vue` (신규)
- `frontend/src/components/game/TileRack.vue`
- `frontend/src/components/game/GameBoard.vue`
- `frontend/src/components/game/TurnActionControl.vue`
- `frontend/src/views/GameView.vue`
- `frontend/src/stores/game.ts`
- `frontend/src/styles/game/rummikub-inspired.css`

## Frontend 테스트

- `frontend/src/__tests__/TurnDraft.spec.ts`
- `frontend/src/__tests__/Phase7CommitFrontend.spec.ts`
- `frontend/src/__tests__/Phase7SecondReviewFrontend.spec.ts` (신규)

## 문서

- `README.md`
- `docs/specs/Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md`
- `docs/specs/Realtime_Tile_Game_Test_Case_Matrix_v1.md`
- `docs/specs/Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_Server_GameState_Model_v1.md`
- `docs/specs/Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- 이 문서와 나머지 Phase 7 Second Review 문서 6개

## 범위 외·변경 없음

- Flyway 신규 Migration 없음. 기존 V5를 그대로 사용한다.
- 상대 Rack 비공개 계약, Draw/PASS, Phase 6 고정 Slot·RAF·Dead Zone·Drag Overlay를 변경하지 않는다.
- Joker 회수·재사용, 게임 종료, Timeout Scheduler는 구현하지 않는다.

