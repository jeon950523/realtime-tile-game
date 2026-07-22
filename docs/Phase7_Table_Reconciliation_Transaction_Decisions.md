# Phase 7 Table Reconciliation 트랜잭션 결정

## 입력 계약

`CommitTurnCommand.tableMelds`는 최종 Table 전체다. 서버는 Candidate의 모든 tileId를 현재 game 범위에서 해석한다.

허용 타일은 baseline의 모든 TABLE 타일과 요청자 Rack에서 이번 턴에 기여한 RACK 타일뿐이다. baseline TABLE 타일은 정확히 한 번 모두 존재해야 한다. 누락, 중복, 다른 게임, 다른 사용자 Rack, POOL 타일은 상태 변경 전에 거부한다. 첫 등록 전에는 baseline Meld가 순서와 내용까지 동일한 prefix여야 한다.

## Rule Engine 연결

Application Service는 RUN/GROUP/점수 규칙을 복사하지 않는다. `GameTurnStateFactory`가 영속 상태와 전체 Candidate를 `TurnState`로 변환하고 기존 `TurnCommitValidator`, `InitialMeldValidator`, `TableRearrangementValidator`가 검증한다.

## 원자적 치환 순서

1. Game 행을 pessimistic lock으로 잠근다.
2. 버전·현재 턴·참가자·타일 집합을 검증한다.
3. Rule Engine으로 전체 Candidate를 검증한다.
4. 기존 Meld와 TABLE 타일의 position을 임시 고가 영역으로 이동해 기존 UNIQUE 제약 충돌을 피한다.
5. Candidate 순서대로 Meld를 재사용하거나 생성하고 type·score·position을 서버 계산값으로 갱신한다.
6. baseline TABLE 타일과 새 Rack 기여 타일을 최종 Meld/position으로 연결한다.
7. 더 이상 참조되지 않는 Meld를 삭제한다.
8. initial flag, 다음 턴, gameVersion, 공개 이벤트를 같은 트랜잭션에서 확정한다.

어느 단계든 예외가 발생하면 DB 트랜잭션 전체가 rollback된다. V5의 `(game_id, position_order)`와 `(game_meld_id, position_order)` 제약을 제거하지 않았으며 V6 Migration은 추가하지 않았다.

`MELDS_COMMITTED`는 `changedMeldIds`, `rackContributionCount`, `tableRecomposed`를 제공한다. Private Snapshot의 typed `tableMelds`가 최종 권위다.

