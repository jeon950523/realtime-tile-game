# Phase 3 Test Case Traceability

작성 기준: 2026-07-15  
최종 Backend: 195 / 최종 Frontend: 56

## 1. ROOM-001~016

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드/Case | 결과 |
|---|---|---|---|---|
| ROOM-001 | 최대 2인 CLASSIC 방 생성 | `RoomApiIntegrationTest` | `room001To003CreatesClassicRoomsForTwoThreeAndFourPlayers(2)` | 통과 |
| ROOM-002 | 최대 3인 CLASSIC 방 생성 | `RoomApiIntegrationTest` | `room001To003CreatesClassicRoomsForTwoThreeAndFourPlayers(3)` | 통과 |
| ROOM-003 | 최대 4인 CLASSIC 방 생성 | `RoomApiIntegrationTest` | `room001To003CreatesClassicRoomsForTwoThreeAndFourPlayers(4)` | 통과 |
| ROOM-004 | 최대 인원 1 차단 | `RoomApiIntegrationTest` | `room004And005RejectsInvalidMaxPlayers(1)` | 통과 |
| ROOM-005 | 최대 인원 5 차단 | `RoomApiIntegrationTest` | `room004And005RejectsInvalidMaxPlayers(5)` | 통과 |
| ROOM-006 | CLASSIC 턴 제한 120초 | `RoomApiIntegrationTest` | `room006ClassicUsesRequestedDefaultTurnLimit` | 통과 |
| ROOM-007 | SPEED·비공개 방 차단 | `RoomApiIntegrationTest` | `room007RejectsSpeedAndPrivateRoom` | 통과 |
| ROOM-008 | 정원 초과 입장 차단 | `RoomApiIntegrationTest` | `room008RejectsJoinWhenRoomIsFull` | 통과 |
| ROOM-009 | WAITING이 아닌 방 입장 차단 | `RoomApiIntegrationTest` | `room009RejectsJoinWhenRoomIsAlreadyPlaying` | 통과 |
| ROOM-010 | 한 사용자 활성 방 1개 | `RoomApiIntegrationTest` | `room010BlocksOneUserFromMultipleActiveRooms` | 통과 |
| ROOM-011 | 방장 이탈 후 결정적 위임 | `RoomApiIntegrationTest` | `room011OwnerLeavingTransfersOwnershipDeterministically` | 통과 |
| ROOM-012 | 참가자 1명 시작 차단 | `RoomApiIntegrationTest` | `room012To014StartEligibilityRequiresOwnerTwoPlayersAndEveryoneReady` | 통과 |
| ROOM-013 | 2명 전원 준비 시작 조건 승인 | `RoomApiIntegrationTest` | 동일 메서드의 승인 Case | 통과 |
| ROOM-014 | 미준비 참가자 존재 시 차단 | `RoomApiIntegrationTest` | 동일 메서드의 `ROOM_PLAYERS_NOT_READY` Case | 통과 |
| ROOM-015 | READY 상태 Room Event | `RoomEventAfterCommitIntegrationTest` | `readyChangePublishesRoomEventAfterCommit` | 통과 |
| ROOM-016 | 입장 후 Lobby ROOM_UPDATED | `RoomEventAfterCommitIntegrationTest` | `playerJoinPublishesRoomAndLobbyUpdateAfterCommit` | 통과 |

## 2. Domain·REST·Repository 추가 검증

| 요구 | 테스트 | 결과 |
|---|---|---|
| 방 생성 기본 WAITING·owner seat 1·NOT_READY | `room001To003CreatesClassicRoomsForTwoThreeAndFourPlayers` | 통과 |
| 방 이름 trim | 동일 테스트의 `"  초보방  "` Case | 통과 |
| 방 이름 길이·제어문자 | `rejectsTooShortAndControlCharacterRoomNames` | 통과 |
| 턴 제한 30~300 | `rejectsInvalidTurnTimeLimits(29/301)` | 통과 |
| 가장 작은 빈 seat 재사용 | `smallestEmptySeatIsReused` | 통과 |
| 마지막 이탈 CLOSED·active room 해제 | `lastPlayerLeavingClosesRoomAndClearsActiveRoom` | 통과 |
| 방장 위임 joinedAt/id 결정성 | `room011OwnerLeavingTransfersOwnershipDeterministically` | 통과 |
| 방 목록·빠른 후보·active-room | `listsWaitingRoomsProvidesQuickMatchAndActiveRoomRecovery` | 통과 |
| 비회원 상세 차단 | `nonMemberCannotReadRoomDetail` | 통과 |
| V2 Migration 존재·컬럼·Entity validate | `MigrationAndRepositoryIntegrationTest` | 통과 |

## 3. 동시성·원자성

| 요구 | 테스트 클래스/메서드 | 결과 |
|---|---|---|
| 동일 사용자 동시 생성 1개 | `RoomConcurrencyIntegrationTest.concurrentRoomCreationBySameUserCreatesOnlyOneActiveMembership` | 통과 |
| 동일 사용자 생성·입장 경쟁 1 Membership | `concurrentCreateAndJoinBySameUserLeavesOneActiveMembership` | 통과 |
| 마지막 자리 동시 입장 1명 | `concurrentJoinForLastSeatAllowsExactlyOneUser` | 통과 |
| 방장 이탈·입장 경쟁 후 방장 1명 | `concurrentOwnerLeaveAndJoinKeepsExactlyOneOwner` | 통과 |
| Commit 후 Lobby Event | `RoomEventAfterCommitIntegrationTest.committedRoomCreationPublishesLobbyEventOnce` | 통과 |
| Rollback 시 Event 없음 | `rolledBackRoomCreationDoesNotPublishAfterCommitEvent` | 통과 |

## 4. WebSocket 인증·인가

| 요구 | 테스트 메서드 | 결과 |
|---|---|---|
| 익명 Health 유지 | `anonymousHealthConnectAndHealthDestinationsRemainPublic` | 통과 |
| 익명 Lobby 거부 | `anonymousLobbySubscriptionIsRejected` | 통과 |
| 유효 JWT Lobby·회원 Room 허용 | `validJwtCanSubscribeLobbyAndMemberRoomTopic` | 통과 |
| 잘못된 JWT CONNECT 거부 | `invalidJwtConnectIsRejectedInsteadOfDowngradedToAnonymous` | 통과 |
| 비회원 Room Topic·READY 거부 | `nonMemberCannotSubscribeRoomOrSendReady` | 통과 |
| BLOCKED 보호 메시지 거부 | `blockedUserIsRejectedOnEveryProtectedMessageUsingCurrentDatabaseStatus` | 통과 |
| DELETED 보호 메시지 거부 | `deletedUserIsRejectedOnEveryProtectedMessageUsingCurrentDatabaseStatus` | 통과 |
| 다른 방 READY 목적지 거부 | `memberCannotSendReadyToAnotherRoom` | 통과 |
| 만료 Principal 거부 | `expiredStompPrincipalIsRejected` | 통과 |
| 실제 익명 Health STOMP 왕복 | 기존 `WebSocketHealthIntegrationTest` | 통과 |

## 5. Action ID

| 요구 | 테스트 | 결과 |
|---|---|---|
| 동일 actionId Domain 실행 1회 | `ActionReplayStoreTest.duplicateActionExecutesStateChangeOnceAndReplaysPrivateReply` | 통과 |
| 동일 READY action Command 실행 1회 | `RoomMessageControllerTest.duplicateReadyActionExecutesDomainCommandOnceAndReplaysReply` | 통과 |
| 중복 개인 Reply 재전송 | 동일 테스트 | 통과 |
| 잘못된 UUID 안전한 개인 거부 | `RoomMessageControllerTest.invalidActionIdReturnsSafePrivateRejection` | 통과 |

## 6. Frontend

| 영역 | 테스트 클래스 | 주요 검증 | 결과 |
|---|---|---|---|
| 초기 목록·실시간 Upsert/Remove | `RoomStore.spec.ts` | REST 초기 조회, ROOM_CREATED/UPDATED/REMOVED | 통과 |
| 생성·중복 클릭 | `RoomStore.spec.ts` | 생성 결과 activeRoom, create Single Flight | 통과 |
| 빠른 입장 없음 | `RoomStore.spec.ts` | 안전한 빈 후보 메시지 | 통과 |
| 참가자 Event | `RoomStore.spec.ts` | JOIN/LEFT/OWNER/READY/CLOSED | 통과 |
| CONNECT Header | `AuthenticatedStompClient.spec.ts` | Bearer Header, URL Query 없음 | 통과 |
| 구독 중복 | `AuthenticatedStompClient.spec.ts` | Lobby/Reply 1회, Room 1개 | 통과 |
| Token 복구 | `AuthenticatedStompClient.spec.ts` | 메모리 Token 없음 시 reissue 1회 | 통과 |
| 재연결 보정 | `AuthenticatedStompClient.spec.ts` | reconnect 후 Lobby Snapshot 콜백 | 통과 |
| Lobby UI | `RoomViews.spec.ts` | 방 카드·입장·Route 이동 | 통과 |
| 생성 Modal | `RoomViews.spec.ts` | trim·CLASSIC·공개방·입력 검증 | 통과 |
| Waiting UI | `RoomViews.spec.ts` | 참가자·빈 좌석·방장·시작 비활성 | 통과 |
| 비방장 UI | `RoomViews.spec.ts` | 시작 버튼 미표시 | 통과 |
| 방 나가기 | `RoomViews.spec.ts` | API 성공 후 `/lobby` | 통과 |
| 로그인 이동 | `AuthenticationViews.spec.ts` | 기본 `/lobby` 이동 | 통과 |
| 비밀번호 Placeholder | `AuthenticationViews.spec.ts` | Phase 2 carryover UI | 통과 |
| Route Guard·복구 | `RouterAuthenticationGuard.spec.ts` | 비인증 차단, active room 복구, 잘못된 ID | 통과 |

## 7. 최종 집계

```text
Backend 기존: 156
Backend 신규: 39
Backend 전체: 195
Failures: 0
Errors: 0
Skipped: 0

Frontend 기존: 34
Frontend 신규: 22
Frontend 전체: 56
TypeScript: 통과
Production Build: 통과
```
