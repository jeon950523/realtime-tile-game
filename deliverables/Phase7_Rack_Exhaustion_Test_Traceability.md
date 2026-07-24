# Phase 7 Rack Exhaustion 테스트 추적표

## Backend

| 요구사항 | 테스트 위치 | 검증 포인트 |
|---|---|---|
| Rack 잔여 시 정상 턴 진행 | `GameTurnCommitIntegrationTest.commit001And003And016Through024_exactThirtyPersistsAtomicallyAndAdvances` | IN_PROGRESS, reason/winner null, 다음 사용자, turnNumber +1, 종료/Room 이벤트 없음 |
| Rack 0 정상 종료 | `GameTurnCommitIntegrationTest.beP7F001RackExhaustionFinishesTheGameAwardsTheWinnerAndReleasesTheRoom` | Game/Player/Room/Active Game/payload/이벤트 전체 계약 |
| 종료 시 턴 미진행 | 위 `beP7F001...` 및 `GameTurnDomainTest.rackExhaustionFinishesTheGameWithoutAdvancingTheTurn` | turnNumber/currentTurnId 불변, 종료 후 advance 거부 |
| 무효 마지막 Meld 롤백 | `GameTurnCommitIntegrationTest.beP7F002InvalidFinalMeldRollsBackWithoutSelectingAWinner` | INVALID_MELD, Rack/Table/Version/winner/event 불변 |
| 종료 후 Draw/Pass/Commit 거부 | `beP7F001...` | 세 명령 모두 GAME_NOT_IN_PROGRESS, 상태 불변 |
| 동시 마지막 Commit | `GameTurnCommitIntegrationTest.beP7F003ConcurrentFinalCommitsTransitionAndPublishTerminationExactlyOnce` | 성공 1, 거부 1, 상태/버전/승자/이벤트 1회 |
| 동일 actionId 재요청 | `GameMessageControllerTest.commit012DuplicateCommitExecutesOnceAndReplaysTheCommittedVersion` | application service 1회 호출, duplicate replay |
| PLAYER_LEFT/FORFEIT 회귀 | `GameExitIntegrationTest`, `GameStore.spec.ts` 기존 테스트 | 기존 종료 상태 및 메시지 계약 |

## Frontend

| 요구사항 | 테스트 위치 | 검증 포인트 |
|---|---|---|
| 내 승리 문구 | `GameStore.spec.ts` — `shows a victory notice when my final valid commit exhausts my Rack` | 정확 문자열, Context 제거, terminalRevision |
| 상대 승리 문구 | `GameStore.spec.ts` — `shows an opponent Rack exhaustion notice to a losing player` | 정확 문자열, terminalRevision |
| 종료 후 Lobby 이동 | `GameView.spec.ts` — `clears the active context and navigates to the lobby after Rack exhaustion termination` | Context 제거, 방 목록 갱신, Lobby 연결, `/lobby` |

## 결정적 Fixture

Backend 통합 테스트는 운영 Endpoint 없이 테스트 코드의 JDBC Fixture로 다음 상태를 만든다.

- 현재 턴 사용자의 Rack을 지정한 유효 타일만 남기도록 재배치
- 정상 종료: `RED 7-8-9`와 `BLUE 1-2-3`의 합계 30 초기 Meld
- 무효 종료 방지: `RED 7-8-10`
- 다른 플레이어 Rack 및 Pool은 운영 테이블 구조를 유지

Fixture는 테스트 범위에만 존재하며 운영 코드나 API를 변경하지 않는다.
