# 실시간 타일 보드게임 서버 GameState 모델 설계 v1

작성 기준: 2026-07-14  
문서 상태: 1차 MVP 구현 기준  
목적: 서버 메모리에서 관리할 실시간 게임 상태와 전이 규칙을 정의한다.

연결 문서:

- `Realtime_Tile_Game_Project_Planning_v1.md`
- `Realtime_Tile_Game_Rules_Spec_v1.md`
- `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `Realtime_Tile_Game_SRS_v1.md`
- `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- `Realtime_Tile_Game_REST_API_Spec_v1.md`
- `Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`

---

# 1. 설계 목표

GameState 모델은 다음을 보장해야 한다.

- 106개 타일의 위치가 항상 정확히 하나여야 한다.
- 상대 손패를 공개 상태와 분리해야 한다.
- 현재 턴과 타이머를 서버가 관리해야 한다.
- 턴 시작 상태를 스냅샷으로 복원할 수 있어야 한다.
- 확정 전 배치는 GameState와 분리해야 한다.
- CLASSIC과 SPEED가 같은 코어를 공유해야 한다.
- gameVersion으로 상태 변경 순서를 추적해야 한다.
- 실패한 요청은 확정 상태를 변경하지 않아야 한다.
- 게임 종료와 랭킹 반영이 중복 실행되지 않아야 한다.

---

# 2. 최상위 구조

```java
public final class GameState {
    private final long gameId;
    private final long roomId;
    private final GameMode gameMode;

    private GameStatus status;
    private long version;

    private final List<GamePlayerState> players;
    private final TilePoolState tilePool;
    private final TableState table;

    private int currentTurnIndex;
    private TurnState currentTurn;

    private Instant gameStartedAt;
    private Instant gameDeadlineAt;
    private Instant gameFinishedAt;

    private int consecutivePassCount;

    private GameResultState result;
}
```

---

# 3. GameState 필드

## 식별

```text
gameId
roomId
gameMode
```

## 상태

```text
status
version
currentTurnIndex
consecutivePassCount
```

## 타일

```text
players[].rack
tilePool
table
```

## 시간

```text
gameStartedAt
gameDeadlineAt
gameFinishedAt
currentTurn.startedAt
currentTurn.deadlineAt
```

## 종료

```text
result
```

---

# 4. GameMode

```java
public enum GameMode {
    CLASSIC,
    SPEED
}
```

모드별 차이는 정책 객체로 분리한다.

```java
public interface GameModePolicy {
    InitialMeldPolicy initialMeldPolicy();
    TurnTimePolicy turnTimePolicy();
    GameEndPolicy gameEndPolicy();
    ScorePolicy scorePolicy();
    boolean affectsRating();
}
```

권장 구현:

```text
ClassicGameModePolicy
SpeedGameModePolicy
```

---

# 5. GameStatus

```java
public enum GameStatus {
    PREPARING,
    IN_PROGRESS,
    FINISHED,
    ABORTED
}
```

허용 전이:

```text
PREPARING → IN_PROGRESS
IN_PROGRESS → FINISHED
IN_PROGRESS → ABORTED
```

역방향 전이는 금지한다.

---

# 6. GamePlayerState

```java
public final class GamePlayerState {
    private final long userId;
    private final int seatOrder;

    private final RackState rack;

    private boolean initialMeldCompleted;
    private ConnectionStatus connectionStatus;

    private int contributedTileScore;

    private int ratingBefore;
}
```

## 설명

### userId

게임 참가자 식별자.

### seatOrder

턴 순서.

```text
1 ~ 4
```

### rack

해당 플레이어의 실제 손패.

서버 내부에서는 전체를 가지지만, 외부 전송 시 해당 사용자에게만 원문을 보낸다.

### initialMeldCompleted

CLASSIC 첫 등록 완료 여부.

SPEED에서는 항상 `true`로 초기화하거나 정책상 검사를 생략한다.

### contributedTileScore

SPEED에서 자기 손패에서 테이블로 확정한 타일 점수 누적.

### ratingBefore

CLASSIC 종료 시 랭킹 계산에 사용할 경기 시작 전 점수.

---

# 7. RackState

```java
public final class RackState {
    private final List<TileId> tileIds;
}
```

## 책임

- 타일 보유 여부
- 추가
- 제거
- 개수
- 종료 점수 계산
- 복사본 생성

## 금지

RackState는 자동 정렬 순서를 서버 게임 규칙으로 보관하지 않는다.

`777`, `789`, 자유 정렬은 클라이언트 전용 표시 상태다.

---

# 8. Tile 모델

```java
public sealed interface Tile permits NumberTile, JokerTile {
    TileId id();
}
```

## NumberTile

```java
public record NumberTile(
    TileId id,
    TileColor color,
    int number
) implements Tile {}
```

## JokerTile

```java
public record JokerTile(
    TileId id
) implements Tile {}
```

## TileId

```java
public record TileId(String value) {}
```

예:

```text
RED-7-A
RED-7-B
JOKER-A
JOKER-B
```

---

# 9. TilePoolState

```java
public final class TilePoolState {
    private final Deque<TileId> remainingTiles;
}
```

## 책임

- 셔플된 타일 보관
- 1개 드로우
- 남은 개수
- 고갈 여부
- 스냅샷 복사

## 주의

클라이언트에는 남은 개수만 공개한다.

실제 순서는 서버 외부로 노출하지 않는다.

---

# 10. TableState

```java
public final class TableState {
    private final List<MeldState> melds;
}
```

## 책임

- 확정된 공개 조합 관리
- 전체 타일 목록 반환
- meld 추가·교체
- 깊은 복사
- 전체 유효성 검증 입력 제공

---

# 11. MeldState

```java
public final class MeldState {
    private final MeldId meldId;
    private final List<TileId> tileIds;
}
```

`meldType`은 저장값으로 신뢰하기보다 서버 검증 결과에서 계산하는 것을 권장한다.

```java
public enum MeldType {
    RUN,
    GROUP
}
```

검증 결과:

```java
public record ValidatedMeld(
    MeldId meldId,
    MeldType type,
    List<TileId> tileIds,
    Map<TileId, JokerAssignment> jokerAssignments
) {}
```

---

# 12. JokerAssignment

```java
public record JokerAssignment(
    TileColor representedColor,
    int representedNumber
) {}
```

조커의 역할은 타일 자체에 영구 저장하지 않는다.

현재 Meld 문맥에서 계산된 결과로 관리한다.

같은 조커가 다른 턴에 다른 값을 대신할 수 있기 때문이다.

---

# 13. TurnState

```java
public final class TurnState {
    private final UUID turnId;
    private final long playerUserId;

    private final Instant startedAt;
    private final Instant deadlineAt;

    private final TurnSnapshot snapshot;

    private boolean resolved;
}
```

## 책임

- 현재 턴 사용자
- 시작·종료 시각
- 롤백 기준 스냅샷
- 중복 종료 방지
- 시간 초과 여부

## resolved

다음 중 하나가 성공하면 `true`.

```text
TURN_COMMIT
DRAW_TILE
PASS
TURN_TIMEOUT
GAME_FINISH
```

한 번 resolved 된 턴은 다시 처리하지 않는다.

---

# 14. TurnSnapshot

```java
public final class TurnSnapshot {
    private final long gameVersion;

    private final TableState table;
    private final Map<Long, RackState> racks;
    private final TilePoolState tilePool;

    private final Map<Long, Boolean> initialMeldCompleted;
    private final Map<Long, Integer> contributedTileScores;

    private final int currentTurnIndex;
    private final int consecutivePassCount;
}
```

## 복원 대상

- 공개 테이블
- 모든 플레이어 손패
- 공용 풀
- 첫 등록 상태
- SPEED 기여 점수
- 현재 턴 인덱스
- 연속 PASS 횟수

## 이유

시간 초과나 실패 중 타일 이동뿐 아니라 점수·첫 등록 상태도 함께 롤백해야 한다.

---

# 15. TurnDraft

1차 권장 구조는 클라이언트 Draft다.

서버는 확정 요청 시 최종 배치만 받는다.

```java
public record TurnCommitRequest(
    UUID actionId,
    long gameId,
    long gameVersion,
    List<MeldDraft> melds
) {}
```

```java
public record MeldDraft(
    String clientMeldId,
    List<TileId> tileIds
) {}
```

서버 내부 검증 객체:

```java
public final class TurnCandidate {
    private final TableState candidateTable;
    private final RackState candidateRack;
    private final Set<TileId> rackToTableTiles;
    private final Set<TileId> retrievedJokers;
}
```

---

# 16. GameState 내부 타일 위치 검증

권장 도우미:

```java
public final class TileLocationIndex {
    private final Map<TileId, TileLocation> locations;
}
```

```java
public sealed interface TileLocation
    permits TilePoolLocation, RackLocation, TableLocation {}
```

예:

```text
RED-7-A → RackLocation(userId=1)
BLUE-4-B → TableLocation(meldId=meld-3)
JOKER-A → TilePoolLocation
```

검증:

```text
locations.size == 106
모든 tileId 1회
중복 없음
유실 없음
```

이 인덱스는 매 요청 시 계산하거나 캐시할 수 있다.

1차에서는 정확성을 우선해 검증 시 재계산해도 된다.

---

# 17. 상태 변경 원칙

모든 게임 변경은 다음 흐름으로 처리한다.

```text
1. GameState 조회
2. 게임 잠금 획득
3. 인증·참가자·현재 턴 검증
4. actionId 중복 검증
5. gameVersion 검증
6. 현재 상태에서 Candidate 생성
7. 규칙 검증
8. 불변조건 검증
9. 새 상태 계산
10. 한 번에 Commit
11. version 증가
12. 이벤트 생성
13. 잠금 해제
```

실패하면 9번 이전 상태를 변경하지 않는다.

---

# 18. 복사 후 교체 방식

권장:

```text
현재 GameState 직접 수정
```

보다

```text
현재 상태에서 Candidate 복사
→ 검증
→ 성공 시 교체
```

방식이 안전하다.

예:

```java
TableState candidateTable = current.table().deepCopy();
RackState candidateRack = currentPlayer.rack().deepCopy();
```

검증 성공 후:

```java
current.replaceTable(candidateTable);
currentPlayer.replaceRack(candidateRack);
```

---

# 19. 동시성 제어

## 기본 단위

```text
게임 ID별 단일 잠금
```

권장 구조:

```java
public final class GameSession {
    private final ReentrantLock lock;
    private GameState state;
}
```

또는:

```text
ConcurrentHashMap<GameId, GameSession>
```

모든 상태 변경은 같은 GameSession lock 안에서 처리한다.

## 읽기

공개 상태 조회는 immutable snapshot을 만들어 반환한다.

---

# 20. GameSessionRegistry

```java
public final class GameSessionRegistry {
    private final ConcurrentHashMap<Long, GameSession> sessions;
}
```

## 책임

- 게임 세션 등록
- 조회
- 종료 세션 제거
- 중복 생성 방지

## 종료 후

DB 결과 저장과 이벤트 전송이 완료된 뒤 즉시 제거하지 않고 짧은 유예시간을 둘 수 있다.

재접속 결과 조회를 위해 DB 결과 API를 사용한다.

---

# 21. CLASSIC 상태 흐름

```text
Game 생성
→ 14개씩 분배
→ 시작 플레이어 결정
→ IN_PROGRESS
→ 턴 시작 Snapshot
→ 배치/드로우/PASS
→ 다음 턴
→ 손패 0개 또는 교착
→ FINISHED
```

## 교착

```text
tilePool empty
AND
consecutivePassCount >= activePlayerCount
```

최저 손패 점수 단독:

```text
WIN
```

최저 점수 동점:

```text
DRAW
```

---

# 22. SPEED 상태 흐름

```text
Game 생성
→ 14개씩 분배
→ 전체 5분 deadline 설정
→ 시작 플레이어 결정
→ 빠른 턴 순환
→ 5분 종료
→ 현재 TurnSnapshot 복원
→ 추가 드로우 없음
→ 최종 점수 계산
→ FINISHED
```

SPEED에서는:

```text
initialMeldCompleted 검사 생략
contributedTileScore 사용
rating 반영 안 함
```

---

# 23. 턴 확정 결과 모델

```java
public record TurnCommitResult(
    GameStateSnapshot publicState,
    Map<Long, PrivatePlayerSnapshot> privateStates,
    boolean gameFinished,
    GameResultState result
) {}
```

공개/개인 응답을 처음부터 분리한다.

---

# 24. 공개 Snapshot

```java
public record PublicGameSnapshot(
    long gameId,
    GameMode gameMode,
    GameStatus status,
    long gameVersion,
    long currentTurnUserId,
    Instant turnDeadlineAt,
    Long remainingGameSeconds,
    List<PublicPlayerSnapshot> players,
    TableSnapshot table,
    int remainingTilePoolCount
) {}
```

## PublicPlayerSnapshot

```java
public record PublicPlayerSnapshot(
    long userId,
    String nickname,
    String avatarType,
    int seatOrder,
    int rackCount,
    boolean initialMeldCompleted,
    ConnectionStatus connectionStatus
) {}
```

상대 실제 손패는 없음.

---

# 25. 개인 Snapshot

```java
public record PrivatePlayerSnapshot(
    long userId,
    List<TileSnapshot> rack,
    boolean canAct,
    boolean initialMeldCompleted
) {}
```

개인 Snapshot은 해당 사용자에게만 전송한다.

---

# 26. GameResultState

```java
public final class GameResultState {
    private final ResultType resultType;
    private final Long winnerUserId;
    private final GameEndReason endReason;
    private final Map<Long, PlayerResultState> playerResults;
}
```

## PlayerResultState

```java
public record PlayerResultState(
    long userId,
    ResultType resultType,
    int remainingTileCount,
    int remainingTileScore,
    Integer contributedTileScore,
    Integer finalSpeedScore
) {}
```

랭킹 계산은 GameState 내부보다 별도 Application Service에서 처리하는 것을 권장한다.

---

# 27. 시간 초과 처리 모델

```java
public final class TurnTimeoutService {
    public TimeoutResult handle(long gameId, UUID turnId);
}
```

검증:

```text
현재 gameId 일치
현재 turnId 일치
currentTurn.resolved == false
현재 서버 시각 >= deadlineAt
```

처리:

```text
snapshot 복원
→ CLASSIC/SPEED 턴 시간 초과 정책 확인
→ 공용 풀 남음: 자동 드로우
→ 공용 풀 없음: PASS
→ 다음 턴 시작
→ version 증가
```

SPEED 전체 종료 시각이 먼저 도달했다면 턴 자동 드로우보다 게임 전체 종료를 우선한다.

---

# 28. 전체 게임 시간과 턴 시간 충돌

예:

```text
SPEED 전체 남은 시간 2초
턴 남은 시간 10초
```

2초 후 전체 게임 종료.

우선순위:

```text
GameDeadline
> TurnDeadline
```

전체 종료 처리:

```text
TurnSnapshot 복원
추가 드로우 없음
점수 계산
FINISHED
```

---

# 29. actionId 처리

권장:

```java
public final class ProcessedActionRegistry {
    private final Map<UUID, ProcessedActionResult> actions;
}
```

게임 세션 내 최근 actionId를 보관한다.

DB `game_action_logs`에도 UNIQUE로 저장한다.

중복 요청:

```text
새 상태 변경 없음
기존 처리 결과 재전송
```

메모리 무한 증가 방지를 위해 게임 종료 후 제거하거나 최대 크기를 둔다.

---

# 30. gameVersion 정책

초기:

```text
0
```

성공한 규칙 상태 변경 후:

```text
version = version + 1
```

증가:

- 게임 시작
- 턴 확정
- 드로우
- PASS
- 턴 시간 초과
- SPEED 전체 종료
- 게임 종료 확정

자동 정렬, 로컬 Draft 편집은 증가하지 않는다.

---

# 31. 재접속

재접속 시 현재 GameState에서 새 Snapshot을 만든다.

```text
PublicGameSnapshot
+
해당 사용자 PrivatePlayerSnapshot
```

클라이언트 Draft는 신뢰하지 않는다.

재접속 시:

```text
클라이언트 Draft 폐기
서버 확정 상태 우선
```

---

# 32. 서버 내부 클래스 구성 권장

```text
game
├─ domain
│  ├─ GameState
│  ├─ GamePlayerState
│  ├─ TurnState
│  ├─ TurnSnapshot
│  ├─ TableState
│  ├─ MeldState
│  ├─ RackState
│  ├─ TilePoolState
│  ├─ Tile
│  ├─ GameResultState
│  └─ policy
│     ├─ GameModePolicy
│     ├─ ClassicGameModePolicy
│     └─ SpeedGameModePolicy
│
├─ application
│  ├─ GameStartService
│  ├─ TurnCommitService
│  ├─ DrawTileService
│  ├─ PassTurnService
│  ├─ TurnTimeoutService
│  ├─ GameFinishService
│  └─ ReconnectService
│
├─ runtime
│  ├─ GameSession
│  ├─ GameSessionRegistry
│  └─ ProcessedActionRegistry
│
└─ presentation
   ├─ websocket
   └─ dto
```

---

# 33. 테스트 요구사항

## GameState 생성

- 2인
- 3인
- 4인
- 총 타일 106개
- 각 손패 14개
- 공용 풀 개수 정확

## 타일 무결성

- 중복 tileId 검출
- 누락 tileId 검출
- 다른 게임 tileId 검출

## TurnSnapshot

- 테이블 복원
- 모든 손패 복원
- tilePool 복원
- 첫 등록 상태 복원
- SPEED 점수 복원

## 동시성

- 같은 게임 동시 확정 1건 성공
- 중복 actionId 재실행 없음
- 다른 게임은 병렬 처리 가능

## 시간 초과

- resolved 턴 재처리 없음
- 잘못 배치 후 복원
- 자동 드로우 1회
- SPEED 전체 종료 우선

## 공개/개인 Snapshot

- 공개 상태에 상대 rack 없음
- 개인 상태에 자기 rack만 존재
- rackCount 일치

---

# 34. 포트폴리오 설명 포인트

이 모델에서 강조할 부분:

1. 실시간 상태와 DB 영속 데이터 분리
2. 게임 ID별 단일 잠금
3. 현재 상태 직접 수정 대신 Candidate 검증 후 Commit
4. TurnSnapshot을 통한 원자적 롤백
5. 공개 Snapshot과 개인 Snapshot 분리
6. 타일 위치 불변조건
7. actionId와 gameVersion의 역할 분리
8. CLASSIC/SPEED 정책 객체 분리
9. 서버 기준 타이머와 전체 게임 시간 우선순위

---

# 35. 다음 문서

다음 단계:

1. 규칙 엔진 클래스 설계
2. 테스트 케이스 상세표
3. 구현 단계별 작업지시서
4. 새 프로젝트 시작 프롬프트
