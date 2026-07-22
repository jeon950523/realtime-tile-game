# Phase 5 Architecture And Transaction Decisions

작성 기준: 2026-07-15 KST

기준 전체본:

```text
phase0715-22-09-phase4-final-clean-source.zip
```

## 1. 확정 상태의 원본

Phase 5도 JPA `Game`, `GamePlayer`, `GameTile`을 서버 권위 상태로 사용한다. 별도 메모리 Game Registry나 수동 version 필드를 추가하지 않는다.

```text
Client Command
→ User ACTIVE 확인
→ Game PESSIMISTIC_WRITE
→ Membership
→ expected gameVersion
→ Current Turn
→ Action 조건
→ Tile·Turn 변경
→ Flush
→ Commit
→ AFTER_COMMIT 공개·개인 Event
```

## 2. Lock·검증 순서

모든 Draw/PASS가 동일한 순서를 사용한다.

1. User ACTIVE
2. Game `PESSIMISTIC_WRITE`
3. Game `IN_PROGRESS`
4. GamePlayer Membership
5. `expectedGameVersion == Game.version`
6. requester가 Current Turn
7. Draw/PASS별 조건

Version을 Current Turn보다 먼저 검증하므로 같은 이전 Version으로 경쟁한 요청은 일관되게 `STALE_GAME_VERSION`이 된다.

## 3. gameVersion

기존 `Game.version`의 JPA `@Version`을 외부 `gameVersion`으로 그대로 사용한다.

- 별도 수동 증가 필드 없음
- `saveAndFlush` 이후 증가값을 ACK·공개 Event·Private State에 사용
- 오래된 Version은 상태 변경 전에 차단

## 4. Draw 원자성

Game Lock을 획득한 뒤 Pool의 `position_order ASC` 첫 행만 추가로 잠근다. 전체 106개를 잠그지 않는다.

```text
첫 Pool Tile
→ 현재 GamePlayer Rack max(position_order) + 1
→ GameTile POOL → RACK
→ 다음 실제 Seat 계산
→ Game.advanceAfterDraw
→ Flush
```

중간 Seat가 비어 있어도 정렬된 참가자 목록에서 다음 Seat를 찾고 마지막 Seat 다음은 첫 Seat로 순환한다.

## 5. PASS 원자성

PASS는 Pool Count가 0일 때만 허용한다.

```text
Pool 0
→ next Player
→ turnNumber +1
→ consecutivePassCount +1
→ 새 turnId·startedAt·deadline
→ gameVersion +1
```

Pool이 남아 있으면 `PASS_NOT_ALLOWED`이며 게임 종료 판정은 하지 않는다.

## 6. Replay

Room Reply에 결합된 기존 Replay Store를 재사용하지 않고 Game 전용 Store를 추가했다.

```text
key = userId + gameId + actionId
TTL = 10분
최대 10,000 Entry
동시 동일 actionId = 동일 Future 결과 공유
```

성공·Business Reject 모두 기존 Room 정책과 동일하게 Replay한다. 단일 서버 메모리 기반이므로 서버 재시작·다중 인스턴스 간 Replay는 보장하지 않는다.

## 7. Event·Privacy

실제 STOMP 전송은 `@TransactionalEventListener(AFTER_COMMIT)`에서만 수행한다.

공개 `TILE_DRAWN`에는 다음이 없다.

```text
tileId
color
number
joker
Pool 전체 순서
```

각 참가자는 `/user/queue/game-state`로 자기 Rack만 포함한 `GamePrivateState`를 받는다.

## 8. N+1 검수

- Game 잠금 조회: Room·CurrentTurnUser EntityGraph
- GamePlayer 조회: User EntityGraph
- GameTile Snapshot 조회: Owner·Owner.User EntityGraph

Snapshot 조립 중 Lazy relation별 추가 조회가 발생하지 않도록 명시 조회했다. 현재 최대 4명·106개이므로 전체 Snapshot 재조립은 허용 범위다.

## 9. Frontend Event Race

- 낮은 Version Event: 무시
- 현재+1 공개 Event: delta 반영
- Version gap: REST Snapshot 복구
- 같은 Version Private State: Rack 상세 반영 허용
- Private State 또는 REST 복구 성공: pending command lock 해제
- Countdown 0: 표시만 하며 자동 행동 없음

## 10. Senior 검수 결과

### 필수 수정 완료

1. Phase 4의 “모든 Rack 정확히 14개” 검증이 Draw 후 Snapshot을 실패시키는 문제를 “최소 초기 Rack 14개 + 전체 106개 위치 무결성”으로 수정했다.
2. 잘못된 next turn 입력에서 Game 일부 필드가 먼저 바뀔 수 있던 부분을 전체 검증 후 일괄 반영으로 수정했다.
3. 성공 Event 수신 후 ACK 유실·재연결 시 Frontend `commandInProgress`가 남을 수 있는 문제를 Private State·REST 복구 시 해제하도록 수정했다.

### 기능적 후속 개선

- Redis 기반 분산 Replay
- Transactional Outbox와 전송 재시도
- Timeout 자동 행동과 TurnSnapshot/TurnDraft
- Game 종료·기권·장기 미접속 정책

### 효율적 후속 개선

- `privateStates` 생성 시 Public State·전체 불변식 검증 1회 공유
- 참가자 전원 Full Private Snapshot 대신 필요 시 Versioned Delta 도입
- Event type/action type 문자열을 enum 또는 공통 상수로 통합
