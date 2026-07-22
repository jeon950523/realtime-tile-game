# Phase 7 Database and Table Meld Contract

## 목적

Phase 7은 확정된 Meld를 서버 권위 상태와 MySQL에 영속화한다. 클라이언트의 TurnDraft는 후보 입력일 뿐이며, Meld 종류와 점수는 서버 Rule Engine 결과만 사용한다.

## V5 스키마

`game_melds`는 한 게임의 확정 Meld 단위를 저장한다.

| 열 | 계약 |
|---|---|
| `game_id` | 소유 게임 |
| `meld_id` | 클라이언트가 생성한 UUID. `(game_id, meld_id)` 유일 |
| `position_order` | Table 내 Meld 순서. `(game_id, position_order)` 유일 |
| `meld_type` | 서버가 판정한 `RUN` 또는 `GROUP` |
| `score` | 서버가 계산한 Meld 점수 |
| `created_by_game_player_id` | Meld를 확정한 참가자 |

`game_tiles.game_meld_id`는 TABLE 타일이 속한 Meld를 가리킨다. `(game_meld_id, position_order)`는 한 Meld 안의 타일 순서를 보장한다.

위치별 링크 불변조건은 다음과 같다.

```text
RACK  -> owner_game_player_id 필수, game_meld_id 없음
POOL  -> owner_game_player_id 없음, game_meld_id 없음
TABLE -> owner_game_player_id 없음, game_meld_id 필수
```

## Typed GameState

`GamePublicState.tableMelds`는 더 이상 임의 객체가 아니다.

```text
GameTableMeldView
- meldId
- meldType
- score
- positionOrder
- tiles: GameTableTileView[]
```

Assembler는 전체 106개 타일의 `RACK ∪ POOL ∪ TABLE` 분할, Meld 링크, Meld·타일 순서, 중복 ID를 검증한다. 상대 Rack 원문은 공개 상태에 포함하지 않는다.

## COMMIT 트랜잭션

한 트랜잭션에서 다음 순서를 지킨다.

1. Game 비관적 잠금과 참가자·턴·`gameVersion` 확인
2. 제출 타일이 현재 사용자 Rack에만 있는지 확인
3. Persistent 상태를 기존 `TurnCommitValidator` 입력으로 변환
4. Rule Engine으로 전체 후보 검증
5. 서버 판정 Meld 종류·점수로 `GameMeld` 저장
6. 제출 타일을 TABLE로 이동하고 Meld·순서 연결
7. 첫 등록이면 `initialMeldCompleted=true`
8. 다음 턴, `turnNumber`, `gameVersion`, deadline, pass count 갱신
9. 공개/개인 Snapshot 조립
10. 트랜잭션 커밋 후 WebSocket 이벤트 발행

어느 단계에서든 실패하면 Meld, 타일 위치, 첫 등록 상태, 턴, 버전이 모두 롤백된다.

## 범위 밖

- 기존 TABLE Meld 재조합
- Joker 회수·재사용
- 게임 종료 판정
- Timeout Scheduler

