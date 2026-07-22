# Phase 4 아키텍처·트랜잭션 결정

작성 기준: 2026-07-15 KST

## 1. 시작 Command 경계

```text
/app/rooms/{roomId}/start
→ RoomMessageController
→ ActionReplayStore
→ GameStartService.startGame
→ Transaction Commit
→ GameStartedCommittedEvent
→ AFTER_COMMIT WebSocket 전송
```

Phase 3의 조건 확인 전용 `RoomCommandService.requestStart()`는 제거했다. Room은 대기방 생성·입장·이탈·READY 책임을 유지하고, 게임 세션 생성은 `GameStartService`로 분리했다.

## 2. 원자적 시작 순서

`GameStartService`는 다음 순서를 하나의 DB 트랜잭션으로 수행한다.

1. 요청 사용자 PESSIMISTIC_WRITE 조회 및 ACTIVE 확인
2. Room PESSIMISTIC_WRITE 조회
3. WAITING·CLASSIC 확인
4. Active RoomPlayer PESSIMISTIC_WRITE 조회 및 seatOrder 정렬
5. Membership·방장·최소 인원·전원 READY·정원 검증
6. `games.room_id` 기존 Game 확인
7. 주입 가능한 `GameStartRandomizer`로 선 플레이어와 106개 순서 결정
8. `Game` 저장
9. `RoomPlayer`를 `GamePlayer` Snapshot으로 저장
10. 기존 `InitialTileDistributor`로 14개씩 Round-Robin 분배
11. `GameTile` 106개 저장
12. Room WAITING → PLAYING
13. DB Count와 타일 중복·Rack·Pool 불변식 재검증
14. Commit 대상 Event 등록

하나라도 실패하면 Game, GamePlayer, GameTile, RoomStatus가 함께 Rollback된다.

## 3. Random 경계

Production 구현은 `SecureGameStartRandomizer`다. 테스트는 `GameStartRandomizer` Fake를 `@Primary`로 주입해 다음을 결정적으로 검증한다.

- 선 플레이어 index
- 셔플 결과 순서
- 각 Rack의 Round-Robin 분배 순서
- Pool 순서의 기초가 되는 전체 Tile 순서

Random을 static 전역 상태로 숨기지 않았다.

## 4. 공개·개인 상태 분리

`GamePublicState`는 다음만 포함한다.

- Game/Room/Mode/Status
- 현재 턴
- Pool Count
- 빈 Table Meld 목록
- 참가자별 Rack Count

`GamePrivateState`만 인증 사용자의 실제 14개 Rack을 포함한다. 상대 타일 ID·색상·숫자는 REST, 공개 Topic, Frontend Store와 DOM에 포함하지 않는다.

## 5. 조회 책임

- `GameQueryService.privateState(gameId, userId)`
- `GameQueryService.activeGame(userId)`

Entity를 직접 응답하지 않고 불변 DTO로 변환한다. User ACTIVE와 GamePlayer Membership을 Application 경계에서 다시 확인한다.

## 6. Event 경계

`GameStartService`는 Spring Application Event만 발행한다. 실제 STOMP 전송은 `AfterCommitGameStartedEventListener`가 담당한다.

Commit 성공 후:

```text
/topic/rooms/{roomId}     GAME_STARTED
/topic/lobby/rooms        ROOM_REMOVED
/topic/games/{gameId}     GAME_STATE_UPDATED (Public)
/user/queue/game-state    GAME_STATE_UPDATED (각 사용자 Private)
```

Rollback 시 위 Event는 0건이다. WebSocket Event 유실 시 REST Active Game과 Private State가 복구 원본이다.

## 7. 동시성과 중복 요청

- 같은 `userId + actionId`: 최초 성공 Reply와 같은 gameId Replay, Domain 실행 1회
- 서로 다른 actionId 동시 START: Room/User Lock으로 직렬화, 성공 1건, 나머지 `ROOM_ALREADY_PLAYING`
- 최종 안전망: `games.room_id UNIQUE`

## 8. Frontend 복구 우선순위

```text
restoreSession
→ GET /api/me/active-game
→ Active Game이면 /games/{gameId}
→ 아니면 GET /api/me/active-room
→ Active Room이면 /rooms/{roomId}
→ 아니면 /lobby
```

WaitingRoom은 방장 개인 Reply가 아니라 Room Topic의 `GAME_STARTED`를 기준으로 모든 참가자를 이동시킨다.

## 9. STOMP 방향 인가

추가 허용:

```text
SUBSCRIBE /topic/games/{gameId}       현재 IN_PROGRESS GamePlayer만
SUBSCRIBE /user/queue/game-state      ACTIVE 인증 사용자
```

계속 거부:

```text
SEND /topic/games/{gameId}
SEND /user/queue/game-state
Game 비회원 SUBSCRIBE
알 수 없는 Destination
```

## 10. 후속 리팩터링 후보

기능적 후보:
- 실제 턴 Command가 도입될 때 `GameStartService`의 초기 조립 부분을 `InitialGameSessionFactory`로 추출해 시작·재시작·테스트 Fixture의 중복을 방지할 수 있다. 현재는 단일 사용처라 과도한 추상화를 피했다.

효율 후보:
- 현재 Private State 조회는 106개 Tile 전체를 읽어 공개 Count와 본인 Rack을 조립한다. 게임 수가 늘어나면 Public Count Projection과 본인 Rack 전용 Query로 나눠 DB 전송량을 줄일 수 있다. 현재 106개 고정 규모에서는 정확성을 우선했다.
- `ActionReplayStore`는 단일 서버 메모리 기반이다. 다중 서버는 이번 범위가 아니며, 해당 단계에서 공유 저장소 또는 idempotency 테이블로 교체해야 한다.
