# Phase 5 Test Case Traceability

작성 기준: 2026-07-15 KST

## 1. 이번 작업 환경의 실제 실행 결과

예상 테스트 수를 통과 수로 기록하지 않는다.

| 검증 | 실제 결과 |
|---|---|
| Backend `sh mvnw -q -DskipTests compile` | Maven 배포본 다운로드 단계에서 `repo.maven.apache.org` DNS 실패, Compile/Test 실행 0 |
| Frontend `npm ci --offline --ignore-scripts` | 오프라인 캐시에 `xmlchars-2.2.0.tgz` 없음, 설치 단계 중단, Test/TypeScript/Build 실행 0 |
| `git diff --check` | 통과 |
| Java changed/new source syntax scan | `javac --release 17 -proc:none` 오류 패턴 스캔 0건 |
| TypeScript changed/new source syntax scan | TypeScript 5.8.3 `transpileModule` 진단 0건 |
| Draw 공개 DTO Privacy scan | `tileId/color/number/joker/poolOrder` 필드 0건 |

위 정적 검수는 Maven Compile, Spring Context, Vitest, Vue Type Check, Production Build를 대체하지 않는다.

## 2. Migration

| 요구 | 테스트 |
|---|---|
| V3 기존 Game Row에 V4 적용 | `GameV4MigrationIntegrationTest.v4BackfillsAnExistingPhase4GameAndEnforcesTurnRuntimeConstraints` |
| Backfill turnId·started·deadline·passCount | 동일 테스트 |
| NOT NULL·passCount Check·deadline Check | 동일 테스트 |
| 신규 Game 최초 Turn Runtime | `GameStartIntegrationTest.ownerStartsAnAtomicGameSnapshotForTwoThreeAndFourPlayers` |

## 3. Draw

| 요구 | 테스트 |
|---|---|
| 현재 사용자 정상 Draw | `GameTurnCommandIntegrationTest.currentPlayerDrawsTheFirstPoolTileToTheRackEndAndAdvancesTheTurn` |
| Pool 첫 position Tile | 동일 테스트 |
| Rack +1 / Pool -1 | 동일 테스트 |
| Rack 끝 position | 동일 테스트 |
| 다른 Tile 105개 불변 | 동일 테스트 |
| 다음 실제 Seat·순환 | 동일 테스트, `GameTurnDomainTest` |
| turnNumber·version +1 | 동일 테스트 |
| 새 turnId·deadline | 동일 테스트 |
| Pool Empty 거부 | `emptyPoolRejectsDrawWithoutChangingState` |
| 비현재 사용자 거부 | `staleVersionIsRejectedBeforeTheCurrentTurnCheck` |
| Draw 후 passCount 0 | `emptyPoolAllowsPassAndDrawAfterwardResetsThePassCount` |
| 잘못된 도메인 입력 부분 변경 0 | `GameTurnDomainTest.invalidNextTurnDoesNotPartiallyMutateTheCurrentTurn` |
| 잘못된 Tile 전이 차단 | `GameTurnDomainTest.onlyAnUnownedPoolTileCanMoveToARackInTheSameGame`, `drawRejectsAPlayerFromAnotherGameWithoutChangingTheTile` |

`DRAW-004` 임시 배치 복원은 TurnDraft 미구현이므로 Phase 7~8로 이월한다.

## 4. Privacy

| 요구 | 테스트 |
|---|---|
| 공개 Event Tile 상세 0 | `GameTurnCommandIntegrationTest.currentPlayerDraws...`, `TileDrawnPayload` Record Component 검증 |
| Draw 사용자 Private Rack에 새 Tile | 동일 테스트 |
| 상대 Private State에는 자기 Rack만 | 동일 테스트 |
| REST 상대 Tile 상세 0 | 동일 테스트의 `GameQueryService.privateState` 회귀 |
| Listener 참가자별 Private State | `AfterCommitGameTurnEventListenerTest` |
| Frontend 공개 Event가 Tile 상세를 추론하지 않음 | `GameStore.spec.ts` same-version Private Rack 테스트 |
| DOM 상대 Tile 상세 미노출 | `GameView.spec.ts` |

## 5. PASS

| 요구 | 테스트 |
|---|---|
| Pool 0 PASS 성공 | `emptyPoolAllowsPassAndDrawAfterwardResetsThePassCount` |
| Pool > 0 거부 | `passIsRejectedWhileThePoolStillContainsTiles` |
| consecutivePassCount +1 | Empty Pool PASS 테스트 |
| 다음 턴·version +1 | Empty Pool PASS 테스트 |
| 게임 종료 안 함 | Service/Domain 범위와 상태 `IN_PROGRESS` 유지 |

## 6. Concurrency·Replay·Atomicity

| 요구 | 테스트 |
|---|---|
| 동일 actionId 상태 변경 1회 | `GameActionReplayStoreTest`, `GameMessageControllerTest` |
| 동시 동일 actionId Future 공유 | `GameActionReplayStoreTest.simultaneousDuplicateActionsShareOneFutureResult` |
| 동일 ACK version Replay | `GameMessageControllerTest.duplicateDrawExecutes...` |
| Reject Replay 정책 | `rejectedCommandIsReplayedConsistentlyWithTheRoomPolicy` |
| stale version 우선 | `staleVersionIsRejectedBeforeTheCurrentTurnCheck` |
| 서로 다른 actionId 같은 Version 성공 1회 | `differentActionsWithTheSameVersionCommitOnlyOnce` |
| Tile 변경 후 실패 Rollback | `beforeCommitFailureRollsBackTileTurnVersionAndAfterCommitEvent` |
| Rollback 시 Event 0 | 동일 테스트 |
| AFTER_COMMIT public/private 전송 | `AfterCommitGameTurnEventListenerTest`, Event Probe 통합 테스트 |

## 7. STOMP Security

| 요구 | 테스트 |
|---|---|
| Game 회원 Draw/PASS 허용 | `StompRoomSecurityIntegrationTest.gameMemberCanSendDrawAndPassWhileANonMemberIsRejected` |
| 비회원 차단 | 동일 테스트 |
| BLOCKED 차단 | `blockedGameMemberCannotSendATurnCommand` |
| Broker Topic 직접 SEND 차단 | 기존 `clientCannotForgeGameTopicOrPrivateGameStateQueue`, `StompBrokerForgeryIntegrationTest` |
| Unknown Destination 차단 | 기존 Security 테스트 |
| 비현재 사용자 Business Reject | `GameTurnCommandIntegrationTest.staleVersionIsRejectedBeforeTheCurrentTurnCheck` |

## 8. Frontend

| 요구 | 테스트 |
|---|---|
| Draw UUID·현재 Version | `GameStore.spec.ts`, `AuthenticatedStompClient.spec.ts` |
| 중복 클릭 방지 | `GameStore.spec.ts` |
| 현재 턴 Draw 활성 | `GameView.spec.ts` |
| 상대 턴 Draw/PASS 비활성 | `GameView.spec.ts` |
| Pool 남음 PASS 비활성 | `GameView.spec.ts` |
| Pool 0 PASS 활성 | `GameView.spec.ts` |
| TILE_DRAWN 공개 상태 반영 | `GameStore.spec.ts` |
| 같은 Version Private Rack 반영 | `GameStore.spec.ts` |
| 낮은 Version 무시 | `GameStore.spec.ts` |
| Version gap·STALE REST 복구 | `GameStore.spec.ts` |
| Countdown 0 Clamp | `GameView.spec.ts` |
| F5 Turn·Version·Deadline 복구 | `GameView.spec.ts`, `loadGame` REST Snapshot |
| Game/Reply Subscription 중복 0 | `AuthenticatedStompClient.spec.ts` |

## 9. 사용자 환경 필수 재실행

```powershell
cd .\backend
.\mvnw.cmd clean test

cd ..\frontend
npm ci
npm run check
```

완료 보고에는 위 명령의 실제 Tests run, Failures, Errors, Skipped, BUILD 결과만 추가한다.
