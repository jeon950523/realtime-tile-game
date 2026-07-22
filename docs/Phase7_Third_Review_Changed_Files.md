# Phase 7 Third Review Changed Files

## Backend production

- `backend/src/main/java/com/realtimetilegame/game/domain/session/GameTile.java`
  - target Meld creator와 Rack owner의 동일인 강제를 제거했다.
- `backend/src/main/java/com/realtimetilegame/game/presentation/GameMessageController.java`
  - 예상하지 못한 runtime 예외를 기록하고 원 actionId의 일반 거절 Reply로 변환한다.

## Backend tests

- `backend/src/test/java/com/realtimetilegame/game/GameMessageControllerTest.java`
  - 내부 예외의 actionId 보존과 상세 메시지 비노출을 검증한다.
- `backend/src/test/java/com/realtimetilegame/game/GameTurnCommitIntegrationTest.java`
  - 교차 플레이어 기존 Meld 연장, 상대 Rack/POOL/중복/누락/invalid rollback, 거절 후 정상 Commit, creator 보존을 검증한다.

## Frontend production

- `frontend/src/realtime/authenticatedStompClient.ts`
  - 게임 구독 준비 신호, publish readiness gate, disconnect/STOMP/WebSocket interruption 통지를 추가했다.
- `frontend/src/stores/game.ts`
  - 명령별 timer와 action type/candidate snapshot, Private State version, authoritative REST recovery를 추가했다.
- `frontend/src/views/GameView.vue`
  - 버튼 활성 조건을 game-command readiness로 통일하고 recovery 결과와 Working Table을 연결했다.
- `frontend/src/components/game/DraftMeld.vue`
  - 타일 아래 버튼/병합 버튼과 전용 계약을 제거하고 Drag 삽입 표시 및 Rack-only 전체 반환 조건을 추가했다.
- `frontend/src/components/game/WorkingTableBoard.vue`
  - 버튼 전용 event/handler를 제거하고 Drag 이동, 새 Meld, 전체 Rack 계약만 유지했다.
- `frontend/src/components/game/TileRack.vue`
  - Working Table의 Rack-origin 타일을 Rack drop으로 반환하는 event를 추가했다.
- `frontend/src/domain/game/turnDraftValidation.ts`
  - Rule validation이 해석한 Joker 위치 점수를 submission score에 재사용한다.
- `frontend/src/types/turnDraft.ts`
  - Meld 검증 결과에 타일별 해석 점수 계약을 추가했다.
- `frontend/src/styles/game/rummikub-inspired.css`
  - 버튼 row/병합 CSS를 제거하고 drag source 및 insertion marker를 추가했다.

## Frontend tests

- `frontend/src/__tests__/Phase7ThirdReviewFrontend.spec.ts`
  - Pending 12건, button-free Working Table 11건, Joker 4건을 추가했다.
- `frontend/src/__tests__/AuthenticatedStompClient.spec.ts`
  - 필수 구독 readiness, game reconnect 중복 방지, WebSocket close recovery 통지를 보강했다.
- `frontend/src/__tests__/GameStore.spec.ts`
- `frontend/src/__tests__/GameView.spec.ts`
- `frontend/src/__tests__/Phase7CommitFrontend.spec.ts`
  - readiness 계약을 기존 회귀 fixture에 반영했다.

## Deleted

- `backend/src/main/java/com/realtimetilegame/game/application/dto/CommitTurnMeldCommand.java`
  - 현재 Working Tree에는 이미 없으며 Patch 적용 대상에서 확실히 제거하도록 manifest에 기록했다.
- `frontend/src/components/game/TurnDraftBoard.vue`
  - Unified Working Table 전환 뒤 사용되지 않던 별도 TurnDraft와 버튼 전용 dead contract를 제거했다.

`DELETE_MANIFEST.txt`가 두 경로를 포함한다.

