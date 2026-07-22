# Phase 7 FINAL Production Closeout — Changed Files

## Root

```text
README.md
```

## Backend Main

```text
backend/src/main/java/com/realtimetilegame/game/application/PersistedTableGridLayoutResolver.java
backend/src/main/java/com/realtimetilegame/game/domain/rule/rearrangement/TableGridLayoutValidator.java
```

## Backend Test

```text
backend/src/test/java/com/realtimetilegame/game/application/PersistedTableGridLayoutResolverTest.java
backend/src/test/java/com/realtimetilegame/game/domain/rule/TableGridLayoutValidatorTest.java
```

## Frontend Component / View

```text
frontend/src/components/game/CommittedTableBoard.vue
frontend/src/components/game/GameBoard.vue
frontend/src/components/game/TileRack.vue
frontend/src/components/game/TurnPreviewBoard.vue
frontend/src/components/game/WorkingTableBoard.vue
frontend/src/views/GameView.vue
```

## Frontend Domain / State / Style

```text
frontend/src/composables/game/useWorkingTable.ts
frontend/src/domain/game/tableFlow.ts                 # 신규
frontend/src/domain/game/tableGrid.ts
frontend/src/styles/game/rummikub-inspired.css
frontend/src/types/turnDraft.ts
```

## Frontend Test

```text
frontend/src/__tests__/GameView.spec.ts
frontend/src/__tests__/Phase7FinalClosureStageB.spec.ts
frontend/src/__tests__/Phase7ProductionCloseout.spec.ts  # 신규
frontend/src/__tests__/Phase7TilePlacementGridFix.spec.ts
```

## Script

```text
scripts/phase7/Verify-Phase7FinalCandidate.ps1          # 신규
```

## Closeout Documents

```text
docs/Phase7_FINAL_Production_Closeout_Implementation_Summary.md
docs/Phase7_FINAL_Production_Closeout_Architecture_And_Contracts.md
docs/Phase7_FINAL_Production_Closeout_Verification_Result.md
docs/Phase7_FINAL_Production_Closeout_Runtime_Verification_Guide.md
docs/Phase7_FINAL_Production_Closeout_Changed_Files.md
docs/Phase7_FINAL_Production_Closeout_Known_Limitations_And_Handoff.md
```

## 변경하지 않은 영역

```text
DB Migration
WebSocket/STOMP Destination와 Payload
Dockerfile / compose.yaml
Kubernetes Manifest
Classroom LAN Script
Native Pointer/Konva Spike
```
