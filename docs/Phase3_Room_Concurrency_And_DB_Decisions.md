# Phase 3 Room Concurrency And DB Decisions

작성 기준: 2026-07-15  
기준 전체본: `phase0715-11-04-phase2-final-game.zip`

## 1. 적용 범위

Phase 3은 대기방의 영속 상태와 동시성만 구현한다.

```text
포함: rooms, room_players, 생성, 입장, 이탈, 방장 위임, 준비 상태
제외: games, game_players, GameState, 타일 분배, PLAYING 전환
```

## 2. V2 Migration

파일:

```text
backend/src/main/resources/db/migration/V2__create_rooms_and_room_players.sql
```

생성 테이블:

```text
rooms
room_players
```

기존 V1 Migration은 수정하지 않았다.

### rooms

- 방 이름, 방장, 최대 인원, 모드, 턴 제한, 공개 여부, 상태를 저장한다.
- Phase 3 생성 모드는 `CLASSIC`, 생성 상태는 `WAITING`이다.
- 마지막 참가자 이탈 시 `CLOSED`와 `closed_at`을 기록한다.
- `PLAYING`과 `FINISHED` 값은 후속 Phase 확장용이며 Phase 3에서 전환하지 않는다.

### room_players

- `left_at IS NULL`을 현재 참가자로 정의한다.
- `seat_order`는 1~4다.
- 새 참가자는 현재 사용하지 않는 가장 작은 좌석 번호를 받는다.
- 생성자 포함 모든 참가자의 초기 준비 상태는 `NOT_READY`다.

## 3. Partial Unique Index를 사용하지 않은 이유

MySQL과 H2에서 같은 의미로 동작하는 조건부 Unique Index를 억지로 구성하지 않았다.

현재 활성 Membership 중복과 좌석 경쟁은 Transaction Lock으로 보장한다.

```text
동일 사용자 경쟁
User Row PESSIMISTIC_WRITE
→ active membership 확인

동일 방 정원·좌석 경쟁
Room Row PESSIMISTIC_WRITE
→ 현재 참가자 잠금 조회
→ 정원 확인
→ 빈 좌석 계산
→ insert
```

이 선택으로 H2 테스트와 MySQL 운영 경계가 같은 Application 규칙을 사용한다.

## 4. Lock 순서

모든 Room Command의 기본 순서는 다음과 같다.

```text
1. 현재 사용자 User Row PESSIMISTIC_WRITE
2. 대상 Room Row PESSIMISTIC_WRITE
3. 현재 Membership 잠금 조회
4. 검증
5. 상태 변경
```

방 생성은 대상 Room이 아직 없으므로 User Lock 후 활성 Membership을 확인하고 생성한다.

이 순서를 통해 다음 경쟁을 차단한다.

- 동일 사용자의 동시 방 생성
- 동일 사용자의 생성·입장 경쟁
- 마지막 자리 동시 입장
- 방장 이탈과 신규 입장 경쟁

## 5. 정원 경쟁

마지막 자리가 하나 남은 방에 두 사용자가 동시에 입장하면 Room Row를 먼저 획득한 Transaction만 정원 검사를 통과한다.

```text
정확히 1명 성공
나머지 1명 ROOM_FULL
```

실제 H2 동시성 테스트에서 성공 1건, `ROOM_FULL` 1건, 최종 인원 정원 일치를 확인했다.

## 6. Seat 결정

좌석은 1부터 `maxPlayers`까지 순서대로 확인한다.

```text
사용 중: 1, 3
신규 좌석: 2
```

이탈한 좌석은 재사용할 수 있다. Client가 좌석 번호를 지정하지 않는다.

## 7. 방장 위임

방장 이탈 후 참가자가 남아 있으면 다음 정렬의 첫 참가자에게 위임한다.

```text
joinedAt ASC
id ASC
```

변경 내용:

```text
기존 방장 RoomPlayer.isOwner = false 상태로 이탈
신규 방장 RoomPlayer.isOwner = true
Room.ownerUserId = 신규 방장
```

준비 상태는 방장 변경 때문에 변경하지 않는다.

방장 이탈과 신규 입장이 동시에 발생해도 최종 활성 방장은 정확히 1명인지 테스트한다.

## 8. 마지막 참가자 이탈

마지막 참가자가 나가면:

```text
RoomPlayer.leftAt 기록
Room.status = CLOSED
Room.closedAt 기록
ROOM_CLOSED
ROOM_REMOVED
```

CLOSED 방은 상세 조회와 입장이 차단된다.

## 9. Event 원자성

Application Service는 Transaction 안에서 Spring Application Event를 등록하고, 실제 STOMP 전송은 다음 Listener만 담당한다.

```text
@TransactionalEventListener(phase = AFTER_COMMIT)
```

검증 결과:

```text
Commit 성공 → Lobby/Room Event 1회
외부 Transaction Rollback → Event 0회
```

따라서 Rollback된 방 상태가 브라우저에 먼저 노출되지 않는다.

## 10. 목록 Query

방 목록은 Entity Collection Fetch Join과 Pagination을 결합하지 않는다.

```text
Projection + 참가자 count 집계
별도 total count
createdAt DESC, roomId DESC
```

빠른 입장 후보:

```text
currentPlayers DESC
createdAt ASC
roomId ASC
```

실제 입장 확정은 후보 조회 결과를 신뢰하지 않고 잠금 후 다시 검사한다.

## 11. 남은 한계

- 서버 재시작 후 대기방 자동 정리는 없다.
- 활성 Membership을 DB Partial Unique Index로 이중 보장하지 않는다.
- 다중 서버 환경에서는 현재 JVM의 actionId 중복 저장소를 공유하지 못한다.
- MySQL 실제 Lock 동작은 사용자 환경에서 병렬 입장으로 직접 확인해야 한다.
- 게임 시작 Transaction은 Phase 4로 이관했다.
