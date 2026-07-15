# 실시간 타일 보드게임 규칙 엔진 클래스 설계 v1

작성 기준: 2026-07-14  
문서 상태: 1차 MVP 구현 기준  
목적: 게임 규칙을 WebSocket, Controller, JPA와 분리해 자동 테스트 가능한 도메인 엔진으로 설계한다.

연결 문서:

- `Realtime_Tile_Game_Project_Planning_v1.md`
- `Realtime_Tile_Game_Rules_Spec_v1.md`
- `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `Realtime_Tile_Game_SRS_v1.md`
- `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- `Realtime_Tile_Game_REST_API_Spec_v1.md`
- `Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`
- `Realtime_Tile_Game_Server_GameState_Model_v1.md`

---

# 1. 설계 목표

규칙 엔진은 다음을 만족해야 한다.

- Spring Framework 없이 단위 테스트 가능
- JPA Entity 없이 실행 가능
- WebSocket DTO와 직접 결합하지 않음
- 한 요청의 전체 유효성을 한 번에 판정
- 실패 시 상태를 변경하지 않음
- 조커의 역할을 문맥에서 계산
- CLASSIC과 SPEED 정책 차이를 분리
- 오류 코드를 일관되게 반환
- 2~4인 여부와 무관하게 동일 엔진 사용

---

# 2. 전체 구조

```text
rule
├─ model
│  ├─ ValidationResult
│  ├─ RuleViolation
│  ├─ ValidatedMeld
│  ├─ JokerAssignment
│  └─ TurnValidationContext
│
├─ meld
│  ├─ MeldValidator
│  ├─ RunValidator
│  ├─ GroupValidator
│  └─ CompositeMeldValidator
│
├─ initial
│  └─ InitialMeldValidator
│
├─ joker
│  └─ JokerRuleValidator
│
├─ rearrangement
│  └─ TableRearrangementValidator
│
├─ turn
│  ├─ TurnCommitValidator
│  ├─ TileIntegrityValidator
│  └─ RackContributionValidator
│
├─ end
│  ├─ WinConditionEvaluator
│  ├─ DeadlockEvaluator
│  └─ SpeedScoreEvaluator
│
└─ policy
   ├─ RulePolicy
   ├─ ClassicRulePolicy
   └─ SpeedRulePolicy
```

---

# 3. 공통 결과 모델

## ValidationResult

```java
public sealed interface ValidationResult<T>
    permits ValidationSuccess, ValidationFailure {
}
```

```java
public record ValidationSuccess<T>(
    T value
) implements ValidationResult<T> {}
```

```java
public record ValidationFailure<T>(
    List<RuleViolation> violations
) implements ValidationResult<T> {}
```

## RuleViolation

```java
public record RuleViolation(
    RuleErrorCode code,
    String message,
    Map<String, Object> details
) {}
```

---

# 4. RuleErrorCode

```java
public enum RuleErrorCode {
    TILE_NOT_FOUND,
    TILE_NOT_OWNED,
    DUPLICATED_TILE,
    MISSING_TILE,

    INVALID_RUN,
    INVALID_GROUP,
    INVALID_MELD,
    INVALID_TABLE_LAYOUT,

    INITIAL_MELD_SCORE_TOO_LOW,
    TABLE_MANIPULATION_NOT_ALLOWED_ON_INITIAL_MELD,

    NO_RACK_TILE_USED,

    INVALID_JOKER_REPLACEMENT,
    RETRIEVED_JOKER_NOT_REUSED,
    JOKER_RETRIEVAL_NOT_ALLOWED,

    DRAW_POOL_EMPTY,
    GAME_ALREADY_FINISHED
}
```

---

# 5. MeldValidator

```java
public interface MeldValidator {
    ValidationResult<ValidatedMeld> validate(
        MeldCandidate candidate,
        TileCatalog tileCatalog
    );
}
```

## MeldCandidate

```java
public record MeldCandidate(
    MeldId meldId,
    List<TileId> tileIds
) {}
```

## TileCatalog

```java
public interface TileCatalog {
    Tile get(TileId tileId);
    boolean contains(TileId tileId);
}
```

---

# 6. RunValidator

## 책임

같은 색상의 연속된 숫자 3개 이상인지 검증한다.

## 인터페이스

```java
public final class RunValidator implements MeldValidator {
    @Override
    public ValidationResult<ValidatedMeld> validate(
        MeldCandidate candidate,
        TileCatalog tileCatalog
    ) {
        // implementation
    }
}
```

## 검증 순서

```text
1. 타일 수 3개 이상
2. 일반 타일 색상 후보 계산
3. 조커 수 계산
4. 중복 tileId 검사
5. 조커를 포함해 가능한 연속 구간 탐색
6. 1~13 범위 확인
7. 13→1 연결 금지
8. 조커 역할 확정
```

## 예시

```text
RED 3, RED 4, RED 5
→ 성공

RED 3, JOKER, RED 5
→ JOKER = RED 4

RED 12, RED 13, RED 1
→ 실패
```

---

# 7. GroupValidator

## 책임

같은 숫자의 서로 다른 색상 3개 또는 4개인지 검증한다.

## 검증 순서

```text
1. 타일 수 3 또는 4
2. 일반 타일 숫자 동일
3. 일반 타일 색상 중복 없음
4. 조커 수 확인
5. 비어 있는 색상 수 확인
6. 조커 역할 배정
```

## 예시

```text
RED 7, BLUE 7, BLACK 7
→ 성공

RED 7, RED 7, BLUE 7
→ 실패

RED 7, BLUE 7, JOKER
→ 성공
```

---

# 8. CompositeMeldValidator

하나의 후보 조합을 RUN 또는 GROUP으로 판정한다.

```java
public final class CompositeMeldValidator {
    private final RunValidator runValidator;
    private final GroupValidator groupValidator;

    public ValidationResult<ValidatedMeld> validate(
        MeldCandidate candidate,
        TileCatalog tileCatalog
    ) {
        // run first, then group or vice versa
    }
}
```

정책:

```text
둘 중 하나만 성공
→ 해당 타입

둘 다 실패
→ INVALID_MELD

둘 다 성공 가능
→ 규칙상 거의 없지만 명시적 우선순위 사용
```

권장 우선순위:

```text
RUN → GROUP
```

---

# 9. ValidatedMeld

```java
public record ValidatedMeld(
    MeldId meldId,
    MeldType meldType,
    List<TileId> tileIds,
    Map<TileId, JokerAssignment> jokerAssignments,
    int score
) {}
```

## score

첫 등록 및 SPEED 점수 계산에 사용한다.

일반 타일은 숫자값.

조커는 대체 숫자값.

---

# 10. InitialMeldValidator

## 책임

CLASSIC 첫 등록 규칙을 검증한다.

```java
public final class InitialMeldValidator {
    public ValidationResult<InitialMeldResult> validate(
        InitialMeldContext context
    ) {
        // implementation
    }
}
```

## InitialMeldContext

```java
public record InitialMeldContext(
    RackState turnStartRack,
    TableState turnStartTable,
    TableState candidateTable,
    List<ValidatedMeld> candidateMelds,
    boolean initialMeldCompleted
) {}
```

## 검증

```text
이미 첫 등록 완료
→ 검사 생략

첫 등록 미완료
→ 기존 테이블 변경 금지
→ 새로 낸 타일이 모두 턴 시작 손패 소유
→ 새 조합 점수 합계 30 이상
```

## 성공 결과

```java
public record InitialMeldResult(
    int totalScore,
    boolean completed
) {}
```

---

# 11. RackContributionValidator

## 책임

일반 턴에서 손패 타일 최소 1개 사용 여부를 검증한다.

```java
public final class RackContributionValidator {
    public ValidationResult<Set<TileId>> validate(
        RackState turnStartRack,
        RackState candidateRack,
        TableState turnStartTable,
        TableState candidateTable
    ) {}
}
```

계산:

```text
turnStartRack - candidateRack
= rackToTableTiles
```

조건:

```text
rackToTableTiles.size >= 1
```

---

# 12. TileIntegrityValidator

## 책임

106개 타일의 중복·유실을 검증한다.

```java
public final class TileIntegrityValidator {
    public ValidationResult<TileIntegrityReport> validate(
        GameStateCandidate candidate,
        Set<TileId> canonicalTileIds
    ) {}
}
```

## 검사

```text
공용 풀
+ 모든 손패
+ 공개 테이블
```

을 합쳐:

```text
총 개수 = 106
고유 개수 = 106
canonicalTileIds와 동일
```

## 오류

```text
DUPLICATED_TILE
MISSING_TILE
TILE_NOT_FOUND
```

---

# 13. TableRearrangementValidator

## 책임

기존 테이블 재조합 규칙을 검증한다.

```java
public final class TableRearrangementValidator {
    public ValidationResult<RearrangementResult> validate(
        RearrangementContext context
    ) {}
}
```

## RearrangementContext

```java
public record RearrangementContext(
    TableState turnStartTable,
    TableState candidateTable,
    RackState turnStartRack,
    RackState candidateRack,
    boolean initialMeldCompleted
) {}
```

## 검증

- 첫 등록 전 재조합 금지
- 기존 테이블 타일이 손패로 이동하지 않음
- 모든 기존 타일이 최종 테이블에 존재
- 손패 타일 최소 1개 사용
- 전체 최종 조합 유효

---

# 14. JokerRuleValidator

## 책임

조커 회수, 교체, 재사용을 검증한다.

```java
public final class JokerRuleValidator {
    public ValidationResult<JokerValidationResult> validate(
        JokerValidationContext context
    ) {}
}
```

## JokerValidationContext

```java
public record JokerValidationContext(
    TableState turnStartTable,
    TableState candidateTable,
    RackState turnStartRack,
    RackState candidateRack,
    boolean initialMeldCompleted,
    List<ValidatedMeld> validatedMelds
) {}
```

## 검증

```text
첫 등록 전 조커 회수 금지
기존 조커가 테이블에서 빠졌는지 탐지
빠졌다면 실제 대체 타일 조건 확인
회수한 조커가 최종 테이블에 다시 존재하는지 확인
손패에 남아 있으면 실패
```

---

# 15. TurnValidationContext

```java
public record TurnValidationContext(
    GameMode gameMode,
    GameState currentState,
    TurnSnapshot turnSnapshot,
    TurnCandidate candidate,
    RulePolicy rulePolicy
) {}
```

---

# 16. TurnCommitValidator

## 전체 오케스트레이터

```java
public final class TurnCommitValidator {
    private final CompositeMeldValidator meldValidator;
    private final TileIntegrityValidator tileIntegrityValidator;
    private final InitialMeldValidator initialMeldValidator;
    private final RackContributionValidator contributionValidator;
    private final TableRearrangementValidator rearrangementValidator;
    private final JokerRuleValidator jokerRuleValidator;

    public ValidationResult<ValidatedTurn> validate(
        TurnValidationContext context
    ) {
        // orchestration
    }
}
```

## 검증 순서

```text
1. 후보 테이블 모든 meld 검증
2. 타일 중복·유실 검증
3. 첫 등록 정책 검증
4. 일반 턴 손패 기여 검증
5. 기존 테이블 재조합 검증
6. 조커 회수·재사용 검증
7. 모드별 점수 계산
8. 승리 조건 평가
```

---

# 17. ValidatedTurn

```java
public record ValidatedTurn(
    TableState committedTable,
    RackState committedRack,
    List<ValidatedMeld> validatedMelds,
    Set<TileId> rackToTableTiles,
    int initialMeldScore,
    int contributedScoreDelta,
    boolean rackEmpty
) {}
```

---

# 18. RulePolicy

```java
public interface RulePolicy {
    boolean requiresInitialMeld();
    int requiredInitialMeldScore();
    boolean allowsTableRearrangementBeforeInitialMeld();
    boolean affectsRating();
}
```

## ClassicRulePolicy

```text
requiresInitialMeld = true
requiredInitialMeldScore = 30
allowsTableRearrangementBeforeInitialMeld = false
affectsRating = true
```

## SpeedRulePolicy

```text
requiresInitialMeld = false
requiredInitialMeldScore = 0
allowsTableRearrangementBeforeInitialMeld = true
affectsRating = false
```

---

# 19. WinConditionEvaluator

```java
public interface WinConditionEvaluator {
    Optional<GameResultState> evaluate(
        GameState state,
        ValidatedTurn validatedTurn
    );
}
```

## CLASSIC

```text
확정 후 현재 플레이어 손패 0개
→ WIN
```

## SPEED

턴 확정 자체로 종료하지 않는다.

전체 5분 타이머가 종료를 결정한다.

---

# 20. DeadlockEvaluator

```java
public final class DeadlockEvaluator {
    public Optional<GameResultState> evaluate(GameState state) {}
}
```

조건:

```text
공용 풀 비어 있음
AND
연속 PASS 수 >= 활성 플레이어 수
```

결과:

```text
최저 손패 점수 단독
→ WIN

최저 점수 동점
→ DRAW
```

---

# 21. SpeedScoreEvaluator

```java
public final class SpeedScoreEvaluator {
    public SpeedScoreResult evaluate(GameState state) {}
}
```

계산:

```text
finalScore
= contributedTileScore
- remainingTileScore
```

조커:

```text
테이블 기여 조커
→ 대체 숫자값

손패 잔여 조커
→ 30점
```

최고 점수 단독:

```text
WIN
```

최고 점수 동점:

```text
DRAW
```

---

# 22. 점수 소유권

SPEED에서는 각 타일이 손패에서 테이블로 처음 확정된 시점을 기록한다.

권장 모델:

```java
public record TileContribution(
    TileId tileId,
    long contributedByUserId,
    int score,
    long committedAtVersion
) {}
```

GameState:

```java
Map<TileId, TileContribution> contributions;
```

기존 테이블 타일 이동은 contribution을 바꾸지 않는다.

턴 롤백 시 contribution 변경도 롤백한다.

---

# 23. 오류 수집 방식

권장 정책:

```text
첫 치명 오류에서 즉시 실패
```

또는

```text
사용자에게 의미 있는 오류 여러 개 수집
```

1차 권장:

- 타일 중복·유실은 즉시 실패
- 각 Meld 검증 오류는 전부 수집 가능
- 첫 등록 점수, 조커 재사용은 별도 오류 추가
- 최대 5개까지만 반환

---

# 24. 순수 함수 우선

다음 클래스는 상태를 직접 수정하지 않는다.

```text
RunValidator
GroupValidator
InitialMeldValidator
TileIntegrityValidator
RackContributionValidator
JokerRuleValidator
DeadlockEvaluator
SpeedScoreEvaluator
```

입력 객체를 받아 결과 객체를 반환한다.

상태 반영은 Application Service에서 수행한다.

---

# 25. TurnCommitService와의 경계

```java
public final class TurnCommitService {
    private final TurnCommitValidator validator;

    public TurnCommitResult commit(TurnCommitCommand command) {
        // lock
        // validate
        // commit
        // version increment
        // event creation
    }
}
```

규칙 엔진:

```text
유효성 판정
점수 계산
결과 후보 생성
```

Application Service:

```text
락
actionId
gameVersion
상태 반영
이벤트
DB 로그
```

---

# 26. 테스트 구조 권장

```text
src/test/java/.../rule
├─ RunValidatorTest
├─ GroupValidatorTest
├─ CompositeMeldValidatorTest
├─ InitialMeldValidatorTest
├─ RackContributionValidatorTest
├─ TileIntegrityValidatorTest
├─ TableRearrangementValidatorTest
├─ JokerRuleValidatorTest
├─ TurnCommitValidatorTest
├─ DeadlockEvaluatorTest
└─ SpeedScoreEvaluatorTest
```

---

# 27. RUN 테스트 핵심

- 3개 연속
- 13개 연속
- 2개 실패
- 색상 불일치
- 숫자 누락
- 숫자 중복
- 13→1
- 조커 중앙
- 조커 시작
- 조커 끝
- 조커 2개
- 해석 불가능한 조커

---

# 28. GROUP 테스트 핵심

- 3색
- 4색
- 2개 실패
- 5개 실패
- 같은 색 중복
- 숫자 불일치
- 조커 1개
- 조커 2개
- 가능한 색상 부족

---

# 29. 첫 등록 테스트 핵심

- 정확히 30
- 29
- 31
- 여러 조합 합산
- 조커 점수
- 기존 테이블 사용
- 기존 테이블 이동
- 이미 완료한 사용자

---

# 30. 재조합 테스트 핵심

- 긴 RUN 분리
- GROUP에 4번째 색 추가
- 기존 타일 다른 조합으로 이동
- 일부 조합만 무효
- 기존 타일 손패 이동
- 손패 타일 미사용
- 조커 포함 재조합

---

# 31. 원자성 테스트

TurnCommitService 통합 테스트에서 확인한다.

```text
검증 실패
→ GameState 동일
→ version 동일
→ 손패 동일
→ 테이블 동일
→ contribution 동일
```

---

# 32. 성능 고려

최대 타일 수는 106개로 작다.

1차에서는:

- 요청마다 전체 테이블 검증
- 요청마다 타일 위치 전체 재계산
- 깊은 복사 후 검증

을 사용해도 충분하다.

정확성과 단순성을 우선한다.

---

# 33. 확장 가능성

후속 모드 추가 시:

```text
GameMode
RulePolicy
GameEndPolicy
ScorePolicy
```

를 추가한다.

예:

```text
BEGINNER
TEAM
NO_JOKER
```

핵심 검증기를 다시 작성하지 않고 정책만 교체할 수 있게 한다.

---

# 34. 포트폴리오 설명 포인트

1. 규칙 엔진과 통신 계층 분리
2. RUN·GROUP 검증기 독립
3. 조커 역할을 Meld 문맥에서 계산
4. TurnCommitValidator가 전체 규칙을 오케스트레이션
5. 검증 성공 전 GameState 미변경
6. CLASSIC/SPEED 정책 분리
7. 106개 전체 검증이 가능한 작은 도메인 특성 활용
8. 자동 테스트 중심 구조

---

# 35. 다음 문서

다음 단계:

1. 테스트 케이스 상세표
2. 구현 단계별 작업지시서
3. 새 프로젝트 시작 프롬프트
4. 최종 기획 문서 인덱스
