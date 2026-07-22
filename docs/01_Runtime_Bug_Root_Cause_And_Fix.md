# Runtime Bug Root Cause And Fix

## 재현 결과

- 한 명이 `게임 포기 및 나가기`를 실행하면 게임 종료 이벤트로 두 사용자 모두 로비로 이동했다.
- 요청자는 `이미 다른 대기방에 참가 중입니다` 오류로 새 방 생성·입장이 막혔다.
- 종료된 기존 방이 로비 목록에 남았다.
- 마지막 사용자가 대기방을 나간 뒤에도 화면이 방 경로에 남는 경우가 있었다.

## 정책과 실제 버그 구분

2인 게임 포기 시 게임이 종료되고 두 사용자 모두 로비로 이동하는 것은 기존 최소 정책에 따른 정상 동작이다.

실제 결함은 다음 세 가지다.

1. 모든 `room_players.left_at`의 영속화가 명시적으로 보장되지 않았다.
2. 게임 종료가 게임 Topic에만 전파되고 Room/Lobby Topic에는 `ROOM_CLOSED`, `ROOM_REMOVED`가 발행되지 않았다.
3. REST 방 나가기는 성공했는데 이후 WebSocket disconnect가 실패하면 프런트가 성공 상태를 적용하지 못했다.

## 수정

- 종료 참가자의 RoomPlayer를 `saveAllAndFlush`로 명시 저장한다.
- Room을 `CLOSED`로 저장한 뒤 after-commit Room/Lobby 이벤트를 함께 발행한다.
- CLOSED room의 남은 membership은 active room 판정에서 제외한다.
- Flyway V8이 과거 terminal game/closed room의 잘못된 membership을 복구한다.
- 프런트는 REST leave 성공 직후 room context와 lobby summary를 먼저 제거한다.
- disconnect는 best-effort cleanup으로 분리한다.
- terminal payload의 roomId를 보존해 로비 전환 전에 해당 방을 즉시 제거한다.
