# Phase 1 테스트 케이스 추적성

작성 기준: 2026-07-15  
기준 전체본: `phase0715-00-39.zip`  
작업: `Phase 1 — Tile Domain And Pure Rule Engine Foundation`

## 1. 실행 결과

```text
Phase 1 순수 도메인 테스트: 108개 통과
기존 Backend 회귀 테스트: 4개 통과
전체 Backend 테스트: 112개 통과
Failures: 0
Errors: 0
Skipped: 0
```

테스트 ID 61개는 아래 테스트 메서드에 직접 연결했다. 하나의 테스트 메서드가 규칙 하나를 대표하며, 추가 경계·불변성 테스트는 별도 표로 관리한다.

## 2. GAME-001~007

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| GAME-001 | 표준 타일 106개와 고유 TileId 106개 | `TileSetFactoryTest` | `game001CreatesExactly106UniqueTileIds` | PASS |
| GAME-002 | 각 색상·숫자 조합의 서로 다른 물리 타일 2개 | `TileSetFactoryTest` | `game002CreatesTwoPhysicalTilesForEveryColorAndNumber` | PASS |
| GAME-003 | 조커 2개 | `TileSetFactoryTest` | `game003CreatesTwoJokers` | PASS |
| GAME-004 | 2인 각 14개, 풀 78개 | `InitialTileDistributorTest` | `game004DistributesFourteenTilesEachToTwoPlayersAndLeaves78` | PASS |
| GAME-005 | 3인 각 14개, 풀 64개 | `InitialTileDistributorTest` | `game005DistributesFourteenTilesEachToThreePlayersAndLeaves64` | PASS |
| GAME-006 | 4인 각 14개, 풀 50개 | `InitialTileDistributorTest` | `game006DistributesFourteenTilesEachToFourPlayersAndLeaves50` | PASS |
| GAME-007 | 초기 분배 후 모든 타일이 정확히 한 위치에 존재 | `InitialTileDistributorTest` | `game007EveryTileExistsAtExactlyOneInitialLocation` | PASS |

## 3. RUN-001~013

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| RUN-001 | R3,R4,R5 성공 | `RunValidatorTest` | `run001AcceptsThreeConsecutiveTilesInSubmittedOrder` | PASS |
| RUN-002 | R1~R13 성공 | `RunValidatorTest` | `run002AcceptsFullOneToThirteenRun` | PASS |
| RUN-003 | 2개 타일 실패 | `RunValidatorTest` | `run003RejectsTwoTiles` | PASS |
| RUN-004 | 색상 불일치 실패 | `RunValidatorTest` | `run004RejectsMixedColors` | PASS |
| RUN-005 | 숫자 누락 실패 | `RunValidatorTest` | `run005RejectsMissingNumber` | PASS |
| RUN-006 | 같은 숫자 중복 실패 | `RunValidatorTest` | `run006RejectsDuplicatedNumberEvenWithDistinctPhysicalTiles` | PASS |
| RUN-007 | 13→1 연결 실패 | `RunValidatorTest` | `run007RejectsThirteenToOneWrap` | PASS |
| RUN-008 | R3,J,R5에서 J=R4 | `RunValidatorTest` | `run008AssignsMiddleJokerFromMeldPosition` | PASS |
| RUN-009 | J,R4,R5에서 J=R3 | `RunValidatorTest` | `run009AssignsLeadingJokerFromMeldPosition` | PASS |
| RUN-010 | R11,R12,J에서 J=R13 | `RunValidatorTest` | `run010AssignsTrailingJokerAsThirteen` | PASS |
| RUN-011 | R12,J,J 범위 초과 실패 | `RunValidatorTest` | `run011RejectsJokersThatWouldContinueBeyondThirteen` | PASS |
| RUN-012 | J,J,J 명시적 실패 | `RunValidatorTest` | `run012RejectsJokerOnlyMeld` | PASS |
| RUN-013 | 동일 물리 TileId 중복 실패 | `RunValidatorTest` | `run013RejectsSamePhysicalTileTwice` | PASS |

## 4. GROUP-001~010

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| GROUP-001 | 서로 다른 3색 성공 | `GroupValidatorTest` | `group001AcceptsThreeDifferentColors` | PASS |
| GROUP-002 | 서로 다른 4색 성공 | `GroupValidatorTest` | `group002AcceptsFourDifferentColors` | PASS |
| GROUP-003 | 2개 타일 실패 | `GroupValidatorTest` | `group003RejectsTwoTiles` | PASS |
| GROUP-004 | 같은 색상 중복 실패 | `GroupValidatorTest` | `group004RejectsDuplicatedColor` | PASS |
| GROUP-005 | 숫자 불일치 실패 | `GroupValidatorTest` | `group005RejectsMixedNumbers` | PASS |
| GROUP-006 | 5개 타일 실패 | `GroupValidatorTest` | `group006RejectsFiveTiles` | PASS |
| GROUP-007 | R7,B7,J 성공 및 가능한 교체 색상 보존 | `GroupValidatorTest` | `group007AssignsOneJokerAndPreservesAllReplaceableColors` | PASS |
| GROUP-008 | R7,J,J 결정적 색상 배정 | `GroupValidatorTest` | `group008AssignsTwoJokersDeterministically` | PASS |
| GROUP-009 | J,J,J 명시적 실패 | `GroupValidatorTest` | `group009RejectsJokerOnlyMeld` | PASS |
| GROUP-010 | 동일 물리 TileId 중복 실패 | `GroupValidatorTest` | `group010RejectsSamePhysicalTileTwice` | PASS |

## 5. INIT-001~011

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| INIT-001 | 첫 등록 정확히 30점 성공 | `InitialMeldValidatorTest` | `init001AcceptsExactlyThirtyPoints` | PASS |
| INIT-002 | 첫 등록 29점 실패 | `InitialMeldValidatorTest` | `init002RejectsTwentyNinePoints` | PASS |
| INIT-003 | 첫 등록 31점 성공 | `InitialMeldValidatorTest` | `init003AcceptsThirtyOnePoints` | PASS |
| INIT-004 | 여러 조합 합산 30점 성공 | `InitialMeldValidatorTest` | `init004SumsMultipleMeldsToThirty` | PASS |
| INIT-005 | 기존 테이블 타일 사용 실패 | `InitialMeldValidatorTest` | `init005RejectsUsingExistingTableTile` | PASS |
| INIT-006 | 기존 테이블 재배치 실패 | `InitialMeldValidatorTest` | `init006RejectsRearrangingExistingTableEvenWhenTilesArePreserved` | PASS |
| INIT-007 | 조커 포함 30점 이상 성공 | `InitialMeldValidatorTest` | `init007AcceptsJokerInInitialMeldAboveThirty` | PASS |
| INIT-008 | 조커 대체 숫자를 점수에 반영 | `InitialMeldValidatorTest` | `init008UsesAssignedJokerNumberInScore` | PASS |
| INIT-009 | 전체 턴 검증 결과에서 첫 등록 완료 전환 | `InitialTurnValidationTest` | `init009TurnValidationMarksClassicInitialMeldCompleted` | PASS |
| INIT-010 | 첫 등록 실패 후 입력 상태 불변 | `InitialMeldValidatorTest` | `init010FailureDoesNotMutateInputStates` | PASS |
| INIT-011 | SPEED 첫 등록 검사 생략 | `InitialTurnValidationTest` | `init011SpeedTurnSkipsThirtyPointThreshold` | PASS |

## 6. TURN-001~009

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| TURN-001 | 손패 타일 1개 이상 사용 시 검증 가능 | `TurnCommitValidatorTest` | `turn001AcceptsOneRackTileContribution` | PASS |
| TURN-002 | 기존 테이블만 재배치 시 `NO_RACK_TILE_USED` | `TurnCommitValidatorTest` | `turn002RejectsTableOnlyRearrangementWithoutRackContribution` | PASS |
| TURN-003 | 긴 RUN 분리 후 최종 전체 유효 시 성공 | `TurnCommitValidatorTest` | `turn003AcceptsSplittingLongRunWhenEveryFinalMeldIsValid` | PASS |
| TURN-004 | GROUP에 네 번째 색상 추가 성공 | `TurnCommitValidatorTest` | `turn004AcceptsAddingFourthColorToGroup` | PASS |
| TURN-005 | 기존 테이블 타일을 손패로 이동하면 실패 | `TurnCommitValidatorTest` | `turn005RejectsMovingExistingTableTileIntoRack` | PASS |
| TURN-006 | Meld 하나라도 무효면 전체 실패 | `TurnCommitValidatorTest` | `turn006RejectsWholeTurnWhenOneMeldIsInvalid` | PASS |
| TURN-007 | 기존 타일 누락 시 `MISSING_TILE` | `TurnCommitValidatorTest` | `turn007RejectsMissingExistingTile` | PASS |
| TURN-008 | 물리 타일 복제 시 `DUPLICATED_TILE` | `TurnCommitValidatorTest` | `turn008RejectsPhysicalTileDuplicatedAcrossValidMelds` | PASS |
| TURN-009 | 검증 실패 후 시작·후보 Snapshot 불변 | `TurnCommitValidatorTest` | `turn009ValidationFailureLeavesBothInputSnapshotsUnchanged` | PASS |

`TURN-010~012`의 실제 version 증가, 다음 턴 전환, 현재 턴 권한 검사는 작업지시서에 따라 후속 런타임 Phase로 이관했다.

## 7. JOKER-001~011

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| JOKER-001 | RUN 중앙 조커 대체 성공 | `JokerRuleValidatorTest` | `joker001RunValidatorCalculatesMiddleReplacement` | PASS |
| JOKER-002 | GROUP 조커 색상 대체 가능성 보존 | `JokerRuleValidatorTest` | `joker002GroupValidatorPreservesPossibleReplacementColors` | PASS |
| JOKER-003 | 정확한 일반 타일로 교체 성공 | `JokerRuleValidatorTest` | `joker003AcceptsExactReplacementAndSameTurnReuse` | PASS |
| JOKER-004 | 잘못된 숫자·색상 교체 실패 | `JokerRuleValidatorTest` | `joker004RejectsWrongReplacementEvenWhenFinalMeldsAreValid` | PASS |
| JOKER-005 | 첫 등록 전 조커 회수 실패 | `JokerRuleValidatorTest` | `joker005RejectsRetrievalBeforeInitialMeldCompletion` | PASS |
| JOKER-006 | 회수한 조커를 다른 유효 Meld에 같은 턴 재사용 | `JokerRuleValidatorTest` | `joker006AcceptsRetrievedJokerReusedInDifferentValidMeld` | PASS |
| JOKER-007 | 회수한 조커를 손패에 보관하면 실패 | `JokerRuleValidatorTest` | `joker007RejectsKeepingRetrievedJokerInRack` | PASS |
| JOKER-008 | 조커 포함 RUN 재분리 후 최종 전체 유효 시 성공 | `JokerRuleValidatorTest` | `joker008AcceptsSplittingJokerRunWhenFinalTableIsValid` | PASS |
| JOKER-009 | 조커 검증 실패 후 입력 위치 불변 | `JokerRuleValidatorTest` | `joker009FailureDoesNotMoveJokerInEitherInputState` | PASS |
| JOKER-010 | SPEED 기여 조커에 확정 당시 대체 숫자 반영 | `SpeedScoreEvaluatorTest` | `joker010UsesAssignedNumberForContributedJoker` | PASS |
| JOKER-011 | SPEED 잔여 조커 30점 감점 | `SpeedScoreEvaluatorTest` | `joker011PenalizesRemainingJokerByThirtyPoints` | PASS |

## 8. 추가 방어·결정성 테스트

| 검증 항목 | 테스트 클래스·메서드 | 결과 |
|---|---|---|
| 표준 세트 반환 Collection 수정 불가 | `TileSetFactoryTest.standardSetCannotBeModifiedExternally` | PASS |
| TileCatalog 중복 ID 생성 차단 | `TileCatalogTest.rejectsDuplicatedTileIdAtConstruction` | PASS |
| TileCatalog 미등록 ID 조회 실패 | `TileCatalogTest.returnsRegisteredTileAndRejectsUnknownId` | PASS |
| TileCatalog 등록 순서 보존 | `TileCatalogTest.preservesRegistrationOrderDeterministically` | PASS |
| 초기 분배 Collection 수정 불가 | `InitialTileDistributorTest.distributionCollectionsAreImmutable` | PASS |
| 참가자 삽입 순서 보존 | `InitialTileDistributorTest.preservesParticipantInsertionOrder` | PASS |
| RUN 입력 순서를 임의 정렬하지 않음 | `RunValidatorTest.doesNotSortSubmittedRunOrder` | PASS |
| RUN 반복 검증 결과 결정적 | `RunValidatorTest.repeatedValidationIsDeterministic` | PASS |
| GROUP 반복 검증 결과 결정적 | `GroupValidatorTest.repeatedGroupValidationIsDeterministic` | PASS |
| Composite RUN 우선·GROUP fallback·전체 실패 | `CompositeMeldValidatorTest` 3개 | PASS |
| ValidationFailure 외부 수정 불가 | `ValidationModelTest.validationFailureViolationsCannotBeModifiedExternally` | PASS |
| Rack에 미소유 타일 추가 차단 | `RackContributionValidatorTest.rejectsCandidateRackContainingTileNotOwnedAtTurnStart` | PASS |
| Rack 제거 후 테이블 미배치 차단 | `RackContributionValidatorTest.rejectsRackTileRemovedWithoutPlacementOnCandidateTable` | PASS |
| 풀에서 테이블로 직접 이동 차단 | `TurnCommitValidatorTest.rejectsMovingTileDirectlyFromPoolToTableEvenWhenTotalIntegrityIsPreserved` | PASS |
| canonical/Catalog 불일치 차단 | `TurnValidationContextTest.rejectsCanonicalSetThatDoesNotMatchCatalog` | PASS |
| GameMode/RulePolicy 불일치 차단 | `TurnValidationContextTest.rejectsModeAndPolicyMismatch` | PASS |
| 검증된 후보 Meld가 아닌 조커 재사용 차단 | `JokerRuleValidatorTest.rejectsReuseThatIsNotBackedByValidatedCandidateMeld` | PASS |
| 전체 106개 위치 무결성 성공·실패 | `TileIntegrityValidatorTest` 2개 | PASS |
| CLASSIC/SPEED 정책 값 분리 | `RulePolicyTest.separatesClassicAndSpeedRules` | PASS |
| Deadlock 조건 미충족·단독 최저·동점 | `DeadlockEvaluatorTest` 4개 | PASS |
| SPEED 단독 최고·동점 | `SpeedScoreEvaluatorTest` 2개 | PASS |
| SPEED 기여 타일이 손패에도 남는 상태 차단 | `SpeedScoreEvaluatorTest.rejectsContributedTileThatStillExistsInARack` | PASS |
| SPEED 동일 잔여 타일의 다중 손패 중복 차단 | `SpeedScoreEvaluatorTest.rejectsSameRemainingTileAcrossParticipantRacks` | PASS |
| SPEED 결과 참가자 순서 결정성 | `SpeedScoreEvaluatorTest.preservesDeterministicParticipantOrderInScoreResult` | PASS |
| 상태 Collection 외부 수정 불가 | `StateImmutabilityTest.stateCollectionsCannotBeModifiedExternally` | PASS |
| `game.domain`의 Spring/JPA/Jackson 의존성 없음 | `PureDomainDependencyTest.gameDomainDoesNotDependOnFrameworkPersistenceOrSerializationPackages` | PASS |
| 같은 MeldId로 조커 역할 변경 우회 차단 | `JokerRuleValidatorTest.sameMeldIdCannotBypassJokerReplacement` | PASS |
| 같은 MeldId 역할 변경 시 실제 교체 타일 요구 | `JokerRuleValidatorTest.sameMeldIdRoleChangeRequiresReplacement` | PASS |
| 같은 MeldId 물리 타일 문맥 변경을 회수·재사용으로 판정 | `JokerRuleValidatorTest.sameMeldIdContextChangeRequiresSameTurnReuse` | PASS |
| 역할·문맥이 유지된 일반 타일 추가는 오탐하지 않음 | `JokerRuleValidatorTest.sameMeldIdWithUnchangedJokerRoleIsNotFalsePositive` | PASS |
| MeldId 이름만 변경해도 규칙 결과 동일 | `JokerRuleValidatorTest.renamingMeldIdAloneCannotChangeRuleOutcome` | PASS |
| 같은 역할이어도 조커 위치 인덱스 변경 시 교체 요구 | `JokerRuleValidatorTest.sameRoleWithChangedJokerIndexRequiresReplacement` | PASS |
| 조커 Assignment Map이 후보 조커 순서를 보존 | `GroupValidatorTest.jokerAssignmentOrderFollowsCandidateJokerOrder` | PASS |
| 첫 등록 기여 TileId가 턴 시작 손패 순서를 보존 | `InitialTurnValidationTest.initialMeldRackContributionOrderFollowsTurnStartRackOrder` | PASS |
| 반복 검증의 Map·Set 순서가 동일 | `InitialTurnValidationTest.repeatedValidationReturnsSameOrderedCollections` | PASS |
| 초기 분배의 null ParticipantId 차단 | `InitialTileDistributorTest.rejectsNullParticipant` | PASS |
| 초기 분배 결과 직접 생성 시 null participant key 차단 | `InitialTileDistributorTest.initialDistributionRejectsNullParticipantKey` | PASS |
| 초기 분배 결과 직접 생성 시 null Rack 차단 | `InitialTileDistributorTest.initialDistributionRejectsNullRack` | PASS |


## 9. 기존 Phase 0 회귀

| 테스트 클래스 | 테스트 수 | 결과 |
|---|---:|---|
| `HealthApiIntegrationTest` | 3 | PASS |
| `WebSocketHealthIntegrationTest` | 1 | PASS |

## 10. 실행 명령

```powershell
cd .\backend
.\mvnw.cmd clean test
```

순수 Phase 1 범위만 실행:

```powershell
.\mvnw.cmd `
  -Dtest="com.realtimetilegame.game.domain.**" `
  test
```

최종 Maven Surefire 집계:

```text
Tests run: 112
Failures: 0
Errors: 0
Skipped: 0
```
