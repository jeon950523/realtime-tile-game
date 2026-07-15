# Phase 1 완료 보고서

작성 기준: 2026-07-15  
최신 기준: `phase0715-00-39.zip`  
작업: `Phase 1 — Tile Domain And Pure Rule Engine Foundation`

## 1. 최종 판정

```text
Phase 1 최종 보완 자동 검증: 통과
사용자 Java 17 clean test: 확인 필요
Phase 2 진행 가능 여부: 사용자 Java 17 검증 전까지 보류
```

이번 결과는 검수 환경에서 **순수 타일 도메인과 규칙 후보 검증 및 최종 보완 자동 테스트가 통과**했음을 의미한다. 사용자 Java 17 환경의 `mvnw.cmd clean test`가 끝나기 전에는 Phase 1을 최종 완료 처리하지 않는다. 실제 게임 상태 Commit, version, actionId, 턴 전환, Draw/Pass, 타이머, API와 WebSocket 연결은 완료된 것으로 간주하지 않는다.

## 2. 구현 범위

### 타일과 상태 기반

- `TileId`, `TileColor`, sealed `Tile`
- `NumberTile`, `JokerTile`
- 결정적 ID의 표준 타일 세트
- `TileCatalog`
- `ParticipantId`, `RackState`, `TilePoolState`
- `MeldId`, `MeldState`, `TableState`
- `TileLocationState`
- 순서가 결정된 입력을 사용하는 `InitialTileDistributor`

### 순수 규칙 엔진

- `ValidationResult`, `RuleViolation`, `RuleErrorCode`
- `RunValidator`, `GroupValidator`, `CompositeMeldValidator`
- `InitialMeldValidator`
- `RackContributionValidator`
- `TileIntegrityValidator`
- `TurnCandidateLocationValidator`
- `TableRearrangementValidator`
- `JokerRuleValidator`
- `TurnCommitValidator`
- `ClassicRulePolicy`, `SpeedRulePolicy`
- `DeadlockEvaluator`
- `SpeedScoreEvaluator`

## 3. 타일 세트

```text
총 타일: 106
고유 TileId: 106
NumberTile: 104
JokerTile: 2
```

결정적 ID 예:

```text
RED-01-A
RED-01-B
...
BLACK-13-A
BLACK-13-B
JOKER-A
JOKER-B
```

조커 객체에는 현재 색상이나 숫자를 저장하지 않는다. 역할은 검증된 Meld의 `JokerAssignment`에서 계산한다.

## 4. 초기 분배

입력 타일의 순서는 호출자가 결정하며 Distributor 내부에서 셔플하지 않는다.

```text
2인: 각 14개 / 공용 풀 78개
3인: 각 14개 / 공용 풀 64개
4인: 각 14개 / 공용 풀 50개
```

분배 후 106개 TileId가 각 손패 또는 공용 풀 중 정확히 한 위치에 존재하는 것을 검증했다.

## 5. 규칙 구현 결과

### RUN

- 길이 3~13
- 동일 색상
- 입력 순서를 실제 왼쪽→오른쪽 순서로 사용
- 숫자 연속성 검증
- 13→1 연결 차단
- 같은 물리 TileId 중복 차단
- 위치 인덱스로 조커 시작 숫자 역산
- 범위를 벗어나는 조커 해석 차단
- JOKER 3개만 존재하는 조합 실패

### GROUP

- 길이 3 또는 4
- 동일 숫자
- 일반 타일 색상 중복 차단
- JOKER 3개만 존재하는 조합 실패
- `RED → BLUE → YELLOW → BLACK` 순서로 대표 색상 결정
- 조커별 가능한 교체 색상 집합 보존

### 첫 등록

CLASSIC:

- 자신의 턴 시작 손패 타일만 사용
- 하나 이상의 유효 Meld
- 총점 30 이상
- 기존 테이블 구조와 TileId 배치 변경 금지
- 조커 대체 숫자를 점수에 반영
- 성공 결과에서 `initialMeldCompletedAfterValidation=true`

SPEED:

- 첫 등록 요구 없음
- 30점 검사 생략

### 일반 턴과 재조합

- 손패 타일 최소 1개 기여
- 후보 손패에 미소유 타일 추가 차단
- 손패에서 제거된 타일의 테이블 미배치 차단
- 공용 풀 또는 다른 참가자 손패 변경 차단
- 기존 테이블 타일의 손패 이동 차단
- 기존 테이블 TileId 누락 차단
- 전체 후보 Meld 검증
- 전체 106개 타일의 미등록·중복·유실 검증

### 조커

- RUN·GROUP 문맥에서 역할 계산
- 첫 등록 전 기존 조커 이동·회수 차단
- 원래 역할에 맞는 일반 타일 교체 검증
- 여러 조커 회수 시 하나의 일반 타일을 중복 교체 근거로 사용하지 않음
- 회수한 동일 Joker TileId의 다른 유효 Meld 재사용 확인
- 손패 보관 또는 검증되지 않은 Meld 재사용 차단

### 종료·점수 순수 계산

CLASSIC Deadlock:

```text
공용 풀 비어 있음
AND 연속 PASS 수 >= 활성 참가자 수
```

- 단독 최저 손패 점수: WIN 후보
- 최저 점수 동점: DRAW 후보

SPEED:

```text
finalScore = contributedTileScore - remainingTileScore
```

- 일반 기여 타일: 실제 숫자와 점수 일치 강제
- 기여 조커: 확정 당시 대체 숫자 1~13
- 잔여 조커: 30점 감점
- 단독 최고 점수: WIN 후보
- 최고 점수 동점: DRAW 후보

## 6. 시니어 검수에서 추가 보강한 항목

### 기능적 보강

1. 전체 106개 무결성만으로 잡히지 않는 **공용 풀→테이블 직접 이동**과 **다른 참가자 손패 변경**을 별도 위치 검증으로 차단했다.
2. `GameMode`와 `RulePolicy`가 서로 다른 조합으로 전달되는 상태를 생성 단계에서 차단했다.
3. `canonicalTileIds`와 `TileCatalog`가 정확히 일치하지 않으면 턴 검증 Context를 생성할 수 없게 했다.
4. 회수한 조커가 실제로 `validatedCandidateMelds`에 포함된 경우에만 같은 턴 재사용으로 인정한다.
5. SPEED에서 기여 타일이 잔여 손패에도 존재하거나, 동일 잔여 타일이 여러 참가자 손패에 중복되는 상태를 차단했다.
6. 일반 타일의 SPEED 기여 점수가 실제 타일 숫자와 다르면 실패하게 했다.

### 효율성과 결정성 보강

- 타일 수가 최대 106개이므로 전체 검증은 O(106) 중심으로 유지했다.
- 조기 캐시·증분 검증은 도입하지 않았다.
- `LinkedHashMap`, `LinkedHashSet`, `EnumSet` 기반 불변 View를 사용해 참가자·타일·조커 색상 결과 순서를 결정적으로 유지했다.
- 전체 상태를 매번 검사하는 현재 방식은 정확성과 단순성이 우선인 1차 규모에 적합하다.

후속 최적화는 실제 게임 Lock 안의 검증 시간이 측정 기준을 초과할 때만 고려한다.


## 6-1. 최종 검수 보완 결과

### 같은 MeldId 조커 우회 차단

기존 구현은 시작과 후보의 `MeldId`만 비교해 같은 문자열을 유지하면 조커 역할과 주변 타일이 완전히 바뀌어도 회수로 판정하지 못했다.

수정 후 조커 보존 여부는 다음을 모두 비교한다.

```text
검증된 JokerAssignment
MeldType
조커 위치 인덱스
기존 물리 TileId 문맥
RUN의 기존 타일 상대 순서
```

따라서 다음 상태는 `INVALID_JOKER_REPLACEMENT` 또는 선행되는 적절한 조커 오류로 거부된다.

```text
시작 M1: RED 5, JOKER, RED 7
후보 M1: BLUE 10, JOKER, BLUE 12
RED 6 교체 타일 없음
```

반대로 MeldId 이름만 바꾸고 역할·타일 문맥·인덱스를 유지한 경우에는 결과가 달라지지 않는다.

### 결과 Collection 순서 결정성

```text
ValidatedMeld.jokerAssignments
→ LinkedHashMap 입력 순서 + unmodifiableMap

TurnCommitValidator의 rackToTableTiles
→ turnStartRack 순서의 LinkedHashSet + unmodifiableSet
```

`game.domain`의 나머지 `Map.copyOf`/`Set.copyOf` 사용도 확인했다.

- `RuleViolation.details`: 상세 key 순서를 계약으로 사용하지 않아 유지
- `RackState.without` 내부 Set: membership 검사 전용이라 유지
- `JokerAssignment.replaceableColors`: `EnumSet`으로 enum 순서가 결정적

### 초기 분배 null 방어

- `InitialTileDistributor`: 참가자 목록 내부 null 차단
- `InitialTileDistribution`: null participant key와 null Rack value 차단

### 신규 테스트

```text
신규 보완 테스트: 12개
Phase 1 순수 도메인 테스트: 108개
전체 Backend 테스트: 112개
```

## 7. 순수 도메인 검증

`backend/src/main/java/com/realtimetilegame/game/domain`에서 다음 의존성을 사용하지 않는다.

```text
org.springframework.*
jakarta.persistence.*
jakarta.validation.*
com.fasterxml.jackson.*
```

검증 결과:

```text
Spring 의존성: 없음
JPA 의존성: 없음
Jakarta Validation 의존성: 없음
Jackson 의존성: 없음
외부 라이브러리 추가: 없음
```

Maven과 별도로 다음 strict compile을 실행했다.

```text
javac --release 17 -Xlint:all -Werror
결과: 성공
```

## 8. 자동 테스트 결과

### 실제 실행 명령

프로젝트 코드와 POM은 변경하지 않고, 실행 환경의 임시 Maven 설치와 로컬 캐시를 사용했다.

```text
mvn -Dtest="com.realtimetilegame.game.domain.**" test
mvn clean test
```

프로젝트의 Maven compiler target:

```text
Java release 17
```

현재 검수 환경의 테스트 실행 JVM:

```text
OpenJDK 21.0.10
```

Java 17 bytecode/API 호환성은 `--release 17` strict compile과 Maven `<java.version>17</java.version>`로 확인했다. 사용자 환경에서는 Java 17로 동일 명령을 다시 실행한다.

### 결과

```text
GAME: 7/7 PASS
RUN: 13/13 PASS
GROUP: 10/10 PASS
INIT: 11/11 PASS
TURN: 9/9 PASS
JOKER: 11/11 PASS
추가 순수 도메인 테스트 포함: 108개 PASS
```

기존 회귀:

```text
HealthApiIntegrationTest: 3개 PASS
WebSocketHealthIntegrationTest: 1개 PASS
```

전체 Maven Surefire:

```text
Tests run: 112
Failures: 0
Errors: 0
Skipped: 0
```

## 9. 검증 실패 후 상태 불변

다음 객체는 생성 시 방어적 복사를 수행하고 setter를 제공하지 않는다.

```text
RackState
TilePoolState
MeldState
TableState
TileLocationState
ValidationFailure
ValidatedMeld
ValidatedTurn
```

실패 경로에서 입력 Rack, Table, Joker 위치를 수정하지 않으며, 테스트에서 검증 전후 객체와 문자열 표현이 동일함을 확인했다.

실제 `GameState.version` 불변은 아직 런타임 Commit이 없으므로 후속 Phase에서 검증한다.

## 10. 변경 영향

```text
REST 변경: 없음
WebSocket endpoint·메시지 변경: 없음
DB·Flyway 변경: 없음
Frontend 변경: 없음
Spring Security 변경: 없음
Dependency·pom.xml 변경: 없음
package-lock.json 변경: 없음
```

Phase 0의 기존 REST, WebSocket, Security, 설정 파일은 수정하지 않았다. 최종 보완에서는 Phase 1이 추가한 순수 `game/domain` 소스 5개와 테스트 4개, Phase 1 문서 4개만 작은 diff로 수정했다.

## 11. 알려진 한계와 후속 Phase 이관

다음은 의도적으로 구현하지 않았다.

- 실제 `TurnCommitService`
- 게임별 Lock
- `gameVersion` 증가와 stale version 검사
- `actionId` 중복 처리
- 현재 턴 사용자 권한 검사
- seatOrder 기준 다음 턴 전환
- Draw/Pass Application Service
- 턴 타이머
- 실제 TurnSnapshot 생성·복원
- 메모리 GameState 저장소
- REST Controller
- WebSocket Handler와 메시지
- JPA Entity, Repository, DB Migration
- 게임 종료 DB 저장, 랭킹, 상대 전적 반영
- SPEED 전체 5분 타이머
- Frontend 게임 화면

## 12. 최종 상태

```text
타일 세트: 통과
2~4인 초기 분배: 통과
RUN/GROUP: 통과
첫 등록·재조합·조커: 통과
전체 타일 무결성: 통과
CLASSIC/SPEED 정책: 통과
Deadlock/SPEED 순수 계산: 통과
입력 상태 불변: 통과
순수 Java 의존성: 통과
기존 Health/WebSocket 회귀: 통과
테스트 ID 추적: 완료
```

최종 판정:

```text
Phase 1 최종 보완 자동 검증 통과
사용자 Java 17 clean test 전까지 조건부 통과
Phase 2 진행 보류
```
