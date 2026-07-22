# Phase 3 WebSocket Authentication And Event Flow

작성 기준: 2026-07-15

## 1. 채널 구분

### 익명 허용 Health

```text
/app/system.health.ping
/topic/system.health
```

Authorization Header가 없는 STOMP CONNECT는 Health 용도의 익명 연결로 유지한다.

### 인증 필수 Lobby

```text
/topic/lobby/rooms
/user/queue/replies
```

### 인증과 Membership 필수 Room

```text
/topic/rooms/{roomId}
/app/rooms/{roomId}/ready
/app/rooms/{roomId}/start
```

## 2. STOMP CONNECT 인증

Frontend는 연결 직전에 Pinia 메모리의 Access Token을 읽는다.

```text
CONNECT Authorization: Bearer <access-token>
```

금지 위치:

```text
WebSocket URL Query
localStorage
sessionStorage
STOMP debug 로그
Application 로그
```

Token이 메모리에 없으면 기존 Refresh Cookie를 이용한 Access Token 재발급을 1회 시도한다.

서버 CONNECT 처리:

```text
Authorization Header 파싱
→ Spring JwtDecoder 검증
→ JWT subject userId 변환
→ DB User 조회
→ 현재 ACTIVE 확인
→ StompPrincipal(userId, expiresAt)
```

명시적으로 잘못된 Token은 익명 연결로 낮추지 않고 거부한다.

## 3. SEND / SUBSCRIBE 재검증

보호 Destination마다 다음을 다시 검사한다.

```text
StompPrincipal 존재
JWT expiresAt > 현재 시각
DB User 존재
DB User.status = ACTIVE
Room Destination이면 활성 Membership 존재
Destination roomId와 Membership roomId 일치
```

따라서 CONNECT 후 사용자가 `BLOCKED` 또는 `DELETED`로 바뀌어도 다음 보호 메시지부터 즉시 거부된다.

Client body의 `userId`는 받거나 신뢰하지 않는다.

## 4. 로비 Event

Subscribe:

```text
/topic/lobby/rooms
```

Event:

```text
ROOM_CREATED  → RoomSummary Upsert
ROOM_UPDATED  → RoomSummary Replace
ROOM_REMOVED  → roomId Remove
```

Frontend는 실시간 Event를 적용하고, Transport 재연결 성공 시 REST 방 목록을 다시 조회해 누락 Event를 보정한다.

## 5. 대기방 Event

Subscribe:

```text
/topic/rooms/{roomId}
```

Event:

```text
ROOM_PLAYER_JOINED
ROOM_PLAYER_LEFT
ROOM_OWNER_CHANGED
ROOM_READY_CHANGED
ROOM_CLOSED
```

Room Topic은 현재 참가자만 구독할 수 있다.

## 6. READY Command

Destination:

```text
/app/rooms/{roomId}/ready
```

Request:

```json
{
  "actionId": "uuid",
  "ready": true
}
```

서버는 Principal의 userId만 사용한다. 동일 준비 상태 요청은 성공하지만 공개 상태 Event를 다시 발행하지 않는다.

## 7. START Command 경계

Destination:

```text
/app/rooms/{roomId}/start
```

검증:

```text
현재 참가자
방장
WAITING
2명 이상
maxPlayers 이하
전원 READY
```

성공 Reply:

```text
ROOM_START_REQUEST_ACCEPTED
```

Phase 3 성공은 시작 가능 조건 승인만 의미한다.

```text
Game Row 생성 없음
gameId 없음
RoomStatus PLAYING 전환 없음
GAME_STARTED 없음
```

## 8. 개인 Reply

Destination:

```text
/user/queue/replies
```

종류:

```text
ROOM_COMMAND_ACCEPTED
ROOM_COMMAND_REJECTED
ROOM_START_REQUEST_ACCEPTED
DUPLICATE_ACTION_REPLAYED
```

오류 Reply는 actionId, code, 안전한 message, recoverable만 노출한다.

## 9. actionId 중복 처리

Key:

```text
userId + actionId
```

저장소:

```text
ConcurrentHashMap
TTL 10분
최대 10,000개
```

동일 actionId 재수신:

```text
Domain Command 재실행 없음
공개 Event 중복 없음
기존 처리 결과를 개인 Reply로 재전송
```

잘못된 UUID는 `INVALID_ROOM_ACTION_ID` 개인 거부 Reply로 처리한다.

서버 재시작 후 이력 복구는 제외다.

## 10. Frontend 연결 수명주기

```text
connectLobby
→ Lobby + User Reply 구독

connectRoom(roomId)
→ Lobby + User Reply + 해당 Room 1개 구독

Room 변경
→ 기존 Room Subscription 해제
→ 새 Room Subscription 1개

Transport 재연결
→ 최신 Access Token으로 CONNECT
→ Subscription 재생성
→ REST Lobby Snapshot 재조회
```

인증 STOMP Client는 기존 익명 Health Client와 분리했다.

## 11. 검증 결과

자동 테스트에서 다음을 확인했다.

- 익명 Health 허용
- 익명 Lobby 거부
- 유효 JWT Lobby 허용
- 잘못된 JWT CONNECT 거부
- 만료 Principal 보호 메시지 거부
- BLOCKED·DELETED 보호 메시지 거부
- 비참가자 Room Topic·READY 거부
- 다른 방 READY Destination 거부
- CONNECT Header Token 사용과 URL Query 미사용
- 연결 중 구독 중복 차단
- 재연결 후 Lobby Snapshot 재조회
