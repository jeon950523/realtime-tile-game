# Phase 4 테스트 추적성

작성 기준: 2026-07-15 KST

## 실행 결과 요약

| 영역 | 결과 |
|---|---|
| Backend `./mvnw clean test` | **실행 전 중단** — Maven 저장소 DNS 해석 실패, 테스트 0개 실행 |
| Backend 소스 기준 예상 총 호출 수 | 기존 221 + 신규 37 = **258**. 실행 통과 수가 아님 |
| Frontend Vitest | **13 files / 77 tests passed** |
| TypeScript | `npm run type-check` 통과 |
| Production Build | `npm run build-only` 통과, 119 modules transformed |
| Java 정적 구문 스캔 | 외부 의존성 부재 오류 외 Java 구문 오류 패턴 0건. Maven Compile 대체 증거가 아님 |

## 신규 Backend 테스트

- 신규 테스트 메서드: **31개**
- Parameterized 호출 포함 소스 기준 신규 호출 수: **37개**

### `com/realtimetilegame/game/AfterCommitGameStartedEventListenerTest.java`

- `sendsLaunchLobbyRemovalPublicStateAndOnePrivateStatePerParticipant`
### `com/realtimetilegame/game/GameApiIntegrationTest.java`

- `participantReadsPrivateStateWithOnlyOwnRackDetails`
- `nonMemberGameRestIsForbidden`
- `activeGameRecoveryReturnsTheStartedGameAndAnonymousStateIsNotExposed`
- `userWithoutActiveGameReceivesAnExplicitEmptyRecoveryResponse`
### `com/realtimetilegame/game/GameEventAtomicityIntegrationTest.java`

- `gameStartedEventRunsAfterACommittedGameOnly`
- `beforeCommitFailureRollsBackRoomGamePlayersTilesAndAfterCommitEvent`
### `com/realtimetilegame/game/GameInitialDealTest.java`

- `distributesFourteenTilesPerRackAndKeepsTheExpectedPool` — 3 cases
- `deterministicOrderDealsRoundRobinFromTheInjectedShuffleResult` — 3 cases
### `com/realtimetilegame/game/GamePersistenceConstraintIntegrationTest.java`

- `gamesRoomIdIsUnique`
- `gamePlayerUserAndSeatAreUniqueInsideOneGame`
- `gameTileIdIsUniqueAndLocationOwnerConstraintIsEnforced`
### `com/realtimetilegame/game/GameStartConcurrencyIntegrationTest.java`

- `concurrentStartsWithDifferentActionsCreateExactlyOneGame`
### `com/realtimetilegame/game/GameStartDeterministicRandomizerIntegrationTest.java`

- `injectedRandomizerFixesFirstPlayerAndRoundRobinRackOrder`
### `com/realtimetilegame/game/GameStartIntegrationTest.java`

- `ownerStartsAnAtomicGameSnapshotForTwoThreeAndFourPlayers` — 3 cases
- `privateStateContainsOnlyTheRequestersRackAndPublicRackCounts`
- `activeGameRecoveryReturnsTheSameGameForEveryParticipant`
- `blockedAndDeletedParticipantsCannotQueryGameState`
- `nonMemberCannotReadPrivateGameState`
- `startRequiresAtLeastTwoPlayersAndEveryoneReady`
- `nonOwnerCannotStartEvenWhenEveryoneIsReady`
- `closedRoomAndAlreadyPlayingRoomCannotStart`
- `waitingRoomLeaveCommandIsBlockedAfterGameStart`
### `com/realtimetilegame/game/RoomGameStartTransitionTest.java`

- `waitingRoomTransitionsToPlayingExactlyOnce`
- `closedRoomCannotTransitionToPlaying`
- `classicGameStartsAtTurnOneWithAParticipantAsCurrentTurn`
### `com/realtimetilegame/room/RoomMessageControllerTest.java`

- `successfulStartActionIsReplayedWithTheSameGameIdWithoutDomainReexecution`
### `com/realtimetilegame/room/StompBrokerForgeryIntegrationTest.java`

- `forgedGameEventIsRejectedAndNotDeliveredToAnotherMember`
### `com/realtimetilegame/room/StompRoomSecurityIntegrationTest.java`

- `gameMemberCanSubscribeOwnGameAndNonMemberIsRejected`
- `clientCannotForgeGameTopicOrPrivateGameStateQueue`
- `authenticatedUserCanSubscribePrivateGameStateQueue`

## 신규 Frontend 테스트

- 신규 테스트: **15개**
- 전체 실제 결과: 기존 62개를 포함해 **77개 통과**

### `AuthenticatedStompClient.spec.ts`

- `subscribes to one game topic and one private game-state queue`
- `gameReconnectLeavesOneActiveGameSubscription`
### `GameApi.spec.ts`

- `loads the authenticated private state for a game`
- `loads active game recovery metadata`
### `GameStore.spec.ts`

- `loads and keeps the private rack separate from public player counts`
- `merges a public event without replacing the authenticated rack`
- `restores an active game before room recovery`
- `ignores a private queue event for another active game`
### `GameView.spec.ts`

- `restores the private game state and one game subscription on mount`
- `shows pool, current turn and opponent rack count without opponent tile details`
### `RoomStore.spec.ts`

- `records GAME_STARTED for every room participant without trusting the owner reply`
### `RoomViews.spec.ts`

- `moves every participant after GAME_STARTED instead of relying on the owner reply`
### `RouterAuthenticationGuard.spec.ts`

- `restores an authenticated user to the active game before the waiting room`
- `validates the active game before restoring a direct game route`
- `redirects a stale game url to the current active game`

## 요구사항 매핑

| 요구사항 | 테스트 증거 |
|---|---|
| 2·3·4인, Rack 14, Pool 78/64/50, 106 unique | `GameInitialDealTest`, `GameStartIntegrationTest` |
| 고정 Fake Random과 선 플레이어·분배 순서 | `GameStartDeterministicRandomizerIntegrationTest` |
| Room PLAYING 전환 | `RoomGameStartTransitionTest`, `GameStartIntegrationTest` |
| 비방장·1명·NOT_READY·CLOSED·PLAYING 거부 | `GameStartIntegrationTest`, 기존 Room API 회귀 |
| V3 Table·Unique·Check | `MigrationAndRepositoryIntegrationTest`, `GamePersistenceConstraintIntegrationTest` |
| Rollback·AFTER_COMMIT | `GameEventAtomicityIntegrationTest` |
| 동시 START 1건 | `GameStartConcurrencyIntegrationTest` |
| 동일 actionId Game 1회·동일 gameId | `RoomMessageControllerTest` |
| 본인 Rack 14·상대 상세 0 | `GameApiIntegrationTest`, `GameStartIntegrationTest`, `GameStore.spec.ts`, `GameView.spec.ts` |
| 비회원 REST/STOMP 차단 | `GameApiIntegrationTest`, `StompRoomSecurityIntegrationTest` |
| Broker Game Event 위조 차단 | `StompBrokerForgeryIntegrationTest` |
| GAME_STARTED 전 참가자 이동 | `RoomStore.spec.ts`, `RoomViews.spec.ts` |
| Active Game 우선 F5 복구 | `RouterAuthenticationGuard.spec.ts`, `GameStore.spec.ts`, `GameView.spec.ts` |
| Game 구독 중복 0 | `AuthenticatedStompClient.spec.ts` |

## 사용자 환경에서 반드시 재실행할 명령

```powershell
cd .\backend
.\mvnw.cmd clean test

cd ..\frontend
npm ci
npm run check
```

Backend는 이 검수 환경에서 실행되지 않았으므로 Java 17에서 실제 총 테스트 수·Failures·Errors·Skipped·BUILD SUCCESS를 확인해야 한다.
