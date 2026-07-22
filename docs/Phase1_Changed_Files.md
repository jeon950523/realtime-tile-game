# Phase 1 변경 파일 목록

작성 기준: 2026-07-15  
기준 전체본: `phase0715-00-39.zip`  
작업: `Phase 1 — Tile Domain And Pure Rule Engine Foundation`  
최종 보완: `Phase 1 Joker Identity Bypass And Deterministic Result Fix`

## 1. 변경 요약

```text
Root 수정: 1개
Backend 신규 main source: 59개
Backend 신규 test source: 20개
Documents 신규: 4개
총 patch 파일: 84개
```

기존 Backend 소스, REST, WebSocket, Security, 설정, DB Migration, `pom.xml`, Frontend, `package-lock.json`은 변경하지 않았다.



## 1-1. 최종 검수 보완 patch 범위

초기 Phase 1 patch의 파일 구조를 유지하면서 아래 13개 파일만 수정했다.

```text
Backend main source: 5개
Backend test source: 4개
Documents: 4개
총 보완 patch 파일: 13개
```

| 경로 | 변경 이유 |
|---|---|
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/joker/JokerRuleValidator.java` | MeldId 문자열이 아니라 검증된 조커 역할, Meld 유형, 조커 인덱스, 기존 물리 타일 문맥으로 이동·회수 여부를 판정한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidatedMeld.java` | 조커 Assignment Map을 입력 순서 그대로 유지하는 불변 `LinkedHashMap` View로 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/TurnCommitValidator.java` | 첫 등록·일반 턴 기여 TileId Set이 턴 시작 손패 순서를 유지하도록 한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/InitialTileDistributor.java` | 참가자 목록 내부의 null `ParticipantId`를 분배 전에 명시적으로 차단한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/InitialTileDistribution.java` | 직접 생성 경계에서도 null 참가자 key와 null Rack value를 차단한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/JokerRuleValidatorTest.java` | 같은 MeldId 우회, 역할·문맥·인덱스 변경, 단순 MeldId 변경 불변 등 조커 회귀 6개를 추가한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/GroupValidatorTest.java` | 후보 조커 입력 순서와 Assignment Map 순서가 일치하는지 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/InitialTurnValidationTest.java` | 손패 기여 순서와 반복 검증 결과 Collection 순서 결정성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/state/InitialTileDistributorTest.java` | null 참가자, null Map key, null Rack value 차단 테스트를 추가한다. |
| `docs/Phase1_Changed_Files.md` | 최종 보완 patch의 정확한 상대 경로와 변경 이유를 기록한다. |
| `docs/Phase1_Test_Case_Traceability.md` | 신규 보완 테스트 12개와 전체 112개 결과를 추적한다. |
| `docs/Phase1_Completion_Report.md` | 조커 우회 차단, 순서 결정성, null 방어 및 재실행 결과를 반영한다. |
| `docs/Phase1_Direct_Verification_Guide.md` | Java 17 최종 재검증 기준과 112개 테스트 기대값을 반영한다. |

REST, WebSocket, DB, Flyway, Frontend, `pom.xml`, 의존성은 변경하지 않았다.

## 2. Root

| 경로 | 변경 | 이유 |
|---|---|---|
| `README.md` | 수정 | 현재 범위를 Phase 1 순수 타일 도메인·규칙 엔진 완료 상태로 갱신하고 Phase 0 기록과 후속 미구현 경계를 유지한다. |

## 3. Backend main source

| 경로 | 변경 이유 |
|---|---|
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/DeadlockContext.java` | 교착 판정의 풀 상태·연속 PASS·손패 점수를 불변으로 전달한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/DeadlockEvaluator.java` | CLASSIC 교착 조건과 단독 최저/DRAW를 순수 계산한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/GameOutcomeCandidate.java` | DB 반영 전 단독 승자 또는 DRAW 후보를 표현한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/OutcomeType.java` | 순수 결과 후보 WIN/DRAW를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/SpeedScoreContext.java` | 기여와 잔여 손패의 소유권·중복·Catalog 일관성을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/SpeedScoreEvaluator.java` | 기여 점수-잔여 점수와 단독 최고/DRAW를 순수 계산한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/SpeedScoreResult.java` | 참가자별 최종 점수와 WIN/DRAW 후보를 결정적 순서로 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/end/TileContribution.java` | SPEED 최초 기여 타일의 소유자·점수·확정 버전 값을 표현한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/initial/InitialMeldContext.java` | 첫 등록 검증에 필요한 손패·테이블·정책 후보를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/initial/InitialMeldResult.java` | 첫 등록 점수와 완료 후보를 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/initial/InitialMeldValidator.java` | CLASSIC 30점, 자기 손패 전용, 기존 테이블 불변을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/joker/JokerRuleValidator.java` | 조커 회수 권한, 기존 역할·Meld 유형·인덱스·물리 타일 문맥 기반 이동 판정, 실제 타일 교체와 같은 턴 유효 Meld 재사용을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/joker/JokerValidationContext.java` | 조커 회수 전후 상태와 검증 완료 후보 Meld를 전달한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/joker/JokerValidationResult.java` | 회수·재사용된 Joker TileId 집합을 결정적으로 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/meld/CompositeMeldValidator.java` | RUN 우선, 실패 시 GROUP으로 Meld 유형을 판정한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/meld/GroupValidator.java` | GROUP 숫자·색상과 결정적 조커 색상 배정을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/meld/MeldValidationSupport.java` | TileId 중복·미등록 공통 해석과 실패 변환을 제공한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/meld/MeldValidator.java` | Meld 검증기의 순수 인터페이스를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/meld/RunValidator.java` | 입력 순서 기반 RUN과 위치 인덱스 기반 조커 역할을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/JokerAssignment.java` | 검증된 Meld 문맥의 조커 숫자·대표 색상·교체 가능 색상을 표현한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/MeldCandidate.java` | 입력 순서를 보존하는 Meld 검증 후보를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/MeldType.java` | 검증 완료 조합의 RUN/GROUP 유형을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/RuleErrorCode.java` | HTTP 오류와 분리된 규칙 엔진 오류 코드를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/RuleViolation.java` | 오류 코드·메시지·상세 정보를 불변 형태로 표현한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/TurnValidationContext.java` | 모드·정책·Catalog·canonical 집합·시작/후보 상태의 일관된 검증 입력을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidatedMeld.java` | 검증 완료 Meld, 조커 역할과 점수를 입력 순서가 보존된 불변 Map으로 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidatedTurn.java` | 실제 Commit 전의 검증 가능한 테이블·손패 후보와 계산 결과를 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidationFailure.java` | 1~5개의 불변 규칙 위반 목록을 보관한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidationResult.java` | 순수 검증 성공·실패 결과의 sealed 계약을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidationResults.java` | 검증 성공·실패 결과 생성을 일관되게 제공한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/model/ValidationSuccess.java` | null이 아닌 성공 값을 보관한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/policy/ClassicRulePolicy.java` | CLASSIC 30점 첫 등록과 랭킹 반영 정책을 제공한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/policy/GameMode.java` | CLASSIC과 SPEED 모드를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/policy/RulePolicy.java` | 모드별 첫 등록·재조합·랭킹 정책 계약을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/policy/SpeedRulePolicy.java` | SPEED 첫 등록 생략과 랭킹 미반영 정책을 제공한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/rearrangement/RearrangementContext.java` | 재조합 검증에 필요한 시작/후보 테이블·손패·정책을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/rearrangement/RearrangementResult.java` | 재조합에서 기여한 손패 타일을 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/rearrangement/TableRearrangementValidator.java` | 기존 타일 보존, 손패 이동 금지와 최종 전체 Meld 유효성을 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/RackContributionValidator.java` | 손패 최소 1개 기여, 미소유 타일과 제거 후 미배치를 차단한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/TileIntegrityReport.java` | 전체 위치의 총 TileId 수와 고유 수를 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/TileIntegrityValidator.java` | canonical 기준 미등록·중복·유실 타일을 전체 위치에서 검증한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/TurnCandidateLocationValidator.java` | 후보가 공용 풀이나 다른 참가자 손패를 변경하지 못하게 한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/rule/turn/TurnCommitValidator.java` | Meld→무결성→첫 등록/일반 턴→재조합→조커 순서의 순수 턴 후보 검증을 오케스트레이션한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/InitialTileDistribution.java` | null 참가자 key·null Rack을 차단하고 초기 분배 결과를 참가자 순서가 보존된 불변 Map으로 반환한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/InitialTileDistributor.java` | null 참가자를 차단하고 호출자가 정한 타일 순서로 2~4인에게 각 14개를 결정적으로 분배한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/MeldId.java` | 테이블 조합의 순수 식별자를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/MeldState.java` | RUN 순서를 보존하면서 잘못된 후보도 표현 가능한 불변 Meld 상태를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/ParticipantId.java` | 규칙 상태에서 사용자 Entity와 분리된 참가자 식별자를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/RackState.java` | 중복 없는 불변 손패 상태와 새 상태 반환 연산을 제공한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/TableState.java` | Meld 목록과 전체 TileId 평탄화 조회를 제공하는 불변 후보 테이블을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/TileLocationState.java` | 공용 풀·참가자 손패·공개 테이블을 합친 순수 위치 Snapshot을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/state/TilePoolState.java` | 순서를 보존하는 불변 공용 타일 풀 상태를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/JokerTile.java` | 역할 상태를 갖지 않는 조커 타일을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/NumberTile.java` | 색상·1~13 숫자 범위를 가진 일반 타일을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/Tile.java` | NumberTile과 JokerTile만 허용하는 sealed 타일 계약을 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/TileCatalog.java` | TileId 조회, 존재 확인, 중복 ID 차단과 등록 순서 보존을 담당한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/TileColor.java` | 표준 타일 색상 4종과 결정적 색상 순서를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/TileId.java` | 공백 없는 물리 타일 식별 값 객체를 정의한다. |
| `backend/src/main/java/com/realtimetilegame/game/domain/tile/TileSetFactory.java` | 결정적 ID를 사용하는 표준 106개 타일 세트를 생성한다. |

## 4. Backend test source

| 경로 | 변경 이유 |
|---|---|
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/CompositeMeldValidatorTest.java` | RUN 우선, GROUP fallback, INVALID_MELD 결과를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/DeadlockEvaluatorTest.java` | 교착 미충족·단독 최저·동점 DRAW를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/GroupValidatorTest.java` | GROUP-001~010과 조커 색상 배정 결정성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/InitialMeldValidatorTest.java` | INIT-001~008·010과 첫 등록 실패 상태 불변을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/InitialTurnValidationTest.java` | INIT-009·011의 전체 턴 결과를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/JokerRuleValidatorTest.java` | JOKER-001~009, 같은 MeldId 조커 우회 차단, 역할·문맥·인덱스 이동 판정과 검증되지 않은 Meld 재사용 차단을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/PureDomainDependencyTest.java` | game.domain의 Spring/JPA/Jackson 의존성 부재를 정적 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/RackContributionValidatorTest.java` | 미소유 손패 타일과 제거 후 미배치 실패를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/RulePolicyTest.java` | CLASSIC/SPEED 정책값과 모드 일치를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/RunValidatorTest.java` | RUN-001~013, 입력 순서 유지와 반복 결정성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/SpeedScoreEvaluatorTest.java` | JOKER-010~011, 단독 최고·DRAW와 점수 상태 무결성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/StateImmutabilityTest.java` | 핵심 상태 Collection의 외부 수정 불가를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/TileIntegrityValidatorTest.java` | 106개 정상 위치와 미등록·중복·유실 오류를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/TurnCommitValidatorTest.java` | TURN-001~009과 공용 풀 직접 이동 차단을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/TurnValidationContextTest.java` | GameMode/RulePolicy 및 canonical/Catalog 불일치를 차단한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/rule/ValidationModelTest.java` | ValidationFailure 위반 목록의 외부 수정 불가를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/state/InitialTileDistributorTest.java` | GAME-004~007, Collection 불변성과 참가자 순서를 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/tile/TileCatalogTest.java` | 중복 ID·미등록 조회·등록 순서 결정성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/domain/tile/TileSetFactoryTest.java` | GAME-001~003과 표준 세트 불변성을 검증한다. |
| `backend/src/test/java/com/realtimetilegame/game/support/RuleTestFixtures.java` | 표준 Catalog와 2인 최소 상태를 만드는 순수 테스트 Fixture를 제공한다. |

## 5. Documents

| 경로 | 변경 | 이유 |
|---|---|---|
| `docs/Phase1_Changed_Files.md` | 신규 | 정확한 patch 상대 경로 84개와 파일별 변경 이유를 기록한다. |
| `docs/Phase1_Test_Case_Traceability.md` | 신규 | GAME/RUN/GROUP/INIT/TURN/JOKER 61개 ID를 실제 테스트 메서드에 연결한다. |
| `docs/Phase1_Completion_Report.md` | 신규 | 구현 범위, 시니어 검수, 자동 테스트, 영향과 후속 이관을 기록한다. |
| `docs/Phase1_Direct_Verification_Guide.md` | 신규 | Java 17 환경에서의 clean test, 의존성 검색과 patch 범위 확인 절차를 제공한다. |

## 6. 변경 없음 확인

```text
backend/pom.xml: 변경 없음
backend/src/main/resources: 변경 없음
기존 Controller: 변경 없음
기존 WebSocket endpoint/Handler: 변경 없음
기존 Security: 변경 없음
DB/Flyway: 변경 없음
frontend 전체: 변경 없음
frontend/package-lock.json: 변경 없음
Dependency: 추가·변경 없음
```

## 7. Patch 제외 항목

```text
.env
.env.local
backend/target
frontend/node_modules
frontend/dist
*.tsbuildinfo
IDE 설정
로컬 로그
임시 Maven 설정과 캐시
```
