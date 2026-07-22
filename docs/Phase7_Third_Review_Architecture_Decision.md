# Phase 7 Third Review Architecture Decision

## Command readiness

Socket transport의 `CONNECTED`는 행동 가능 상태가 아니다. Game command readiness는 다음의 conjunction이다.

```text
socket connected
+ current game public subscription
+ private game-state subscription
+ shared user reply subscription
+ initial private state loaded
```

다른 game/lobby/room으로 전환하거나 transport가 끊기면 readiness를 먼저 false로 만든다.

## Pending lifecycle

각 명령은 actionId, baseVersion, actionType을 가진다. Commit은 recovery 비교용 전체 Candidate Table snapshot도 보관한다. timer는 명령당 하나이며 새 명령, Reply, Private State, route leave, disconnect, active game 변경, store test reset에서 이전 timer를 제거한다.

```text
publish
  -> rejected: local Candidate 유지, Pending 해제
  -> accepted + matching Private State: authoritative sync
  -> accepted + 1.5s State 유실: REST recovery
  -> 9s Reply/State 유실: REST recovery
  -> transport interruption: REST recovery
```

REST 결과가 baseVersion보다 높고 Commit Candidate와 Table이 정확히 일치할 때만 COMMITTED로 판정한다. version이 그대로면 Candidate를 유지해 재시도한다. version이 높지만 Candidate가 다르면 authoritative reset으로 처리한다. 어느 경로도 명령을 자동 재전송하지 않는다.

## Creator and ownership

`GameMeld.createdBy`는 Meld의 최초 생성 메타데이터이지 그 Meld에 앞으로 놓일 모든 타일의 소유권이 아니다. 타일 제출 권한은 Candidate reconciliation에서 현재 요청자의 Rack 소유 여부로 검증한다. 따라서 기존 Meld creator는 보존하면서 다음 플레이어의 합법적인 Rack 기여를 허용한다.

## Working Table UX

단일 authority는 Unified Working Table이다. 타일 조작은 HTML drag payload `application/x-working-tile-id`와 drop target으로 수행한다. per-tile 버튼은 제거하되 Domain composable의 일반 reorder/split/merge 함수는 테스트 및 향후 접근성 adapter를 위해 유지한다.

Rack drop은 `returnTile`을 호출하며 composable이 authoritative Rack ID인지 다시 검사한다. 새 Meld의 전체 반환은 모든 tile ID가 Rack map에 있을 때만 노출한다.

## Joker score

RUN/GROUP validation 결과는 각 tileId의 해석 숫자를 함께 반환한다. Meld score와 submission score가 같은 해석 결과를 공유하므로 Rack Joker를 0으로 계산하는 별도 규칙 분기가 없다.

