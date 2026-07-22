# 실시간 타일 보드게임 WebSocket 메시지 명세 v1

작성 기준: 2026-07-14  
문서 상태: 1차 MVP + CLASSIC/SPEED + 재접속 + 랭킹 결과 연계 고려  
권장 프로토콜: WebSocket + STOMP  
인증: STOMP CONNECT 헤더 또는 Handshake 인증

연결 문서:

- `Realtime_Tile_Game_Project_Planning_v1.md`
- `Realtime_Tile_Game_Rules_Spec_v1.md`
- `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `Realtime_Tile_Game_SRS_v1.md`
- `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- `Realtime_Tile_Game_REST_API_Spec_v1.md`

---

# 1. 설계 목표

WebSocket 계층은 다음을 보장해야 한다.

- 로비와 대기방 상태를 실시간으로 동기화한다.
- 게임 상태의 최종 판단은 서버가 수행한다.
- 공개 정보와 개인 정보를 분리한다.
- 상대 손패 원문을 절대 브로드캐스트하지 않는다.
- 동일 actionId의 요청을 중복 처리하지 않는다.
- 오래된 gameVersion 요청을 거부한다.
- 시간 초과, 재접속, 게임 종료를 서버 기준으로 처리한다.
- 2~4인 CLASSIC과 SPEED를 동일한 메시지 구조로 지원한다.

---

# 2. 연결 엔드포인트

```text
/ws
```

SockJS 사용 여부는 선택 사항이다.

권장:

```text
Native WebSocket 우선
필요 시 SockJS fallback
```

---

# 3. STOMP Prefix

## Client Send

```text
/app
```

## Public Subscribe

```text
/topic
```

## Private Subscribe

```text
/user/queue
```

---

# 4. 인증

## CONNECT Header 예시

```text
Authorization: Bearer <access-token>
```

서버는 연결 시 다음을 검증한다.

- 토큰 유효성
- 사용자 상태
- 사용자 ID
- 동일 계정 중복 게임 연결
- 진행 중 세션 존재 여부

인증 실패 시 연결을 거부한다.

---

# 5. 공통 요청 Envelope

모든 게임 변경 요청은 다음 형식을 사용한다.

```json
{
  "actionId": "4d2ab67e-ef15-4d32-8a17-9d728e480fa9",
  "gameId": 33,
  "gameVersion": 37,
  "actionType": "TURN_COMMIT",
  "clientSentAt": "2026-07-14T20:30:00+09:00",
  "payload": {}
}
```

## 필드

| 필드 | 필수 | 설명 |
|---|---:|---|
| actionId | O | UUID, 중복 요청 방지 |
| gameId | O | 대상 게임 |
| gameVersion | O | 클라이언트가 알고 있는 최신 버전 |
| actionType | O | 요청 유형 |
| clientSentAt | X | 디버깅 참고용 |
| payload | O | 요청별 데이터 |

서버 시간 판정에는 `clientSentAt`을 신뢰하지 않는다.

---

# 6. 공통 성공 Event Envelope

```json
{
  "eventId": "server-event-uuid",
  "eventType": "TURN_COMMITTED",
  "gameId": 33,
  "gameVersion": 38,
  "serverTime": "2026-07-14T20:30:01+09:00",
  "payload": {}
}
```

---

# 7. 공통 실패 Event Envelope

개인 채널로만 보낸다.

```json
{
  "eventId": "server-event-uuid",
  "eventType": "ACTION_REJECTED",
  "gameId": 33,
  "gameVersion": 37,
  "serverTime": "2026-07-14T20:30:01+09:00",
  "payload": {
    "actionId": "4d2ab67e-ef15-4d32-8a17-9d728e480fa9",
    "errorCode": "INVALID_TABLE_LAYOUT",
    "message": "테이블에 유효하지 않은 조합이 있습니다.",
    "recoverable": true
  }
}
```

---

# 8. 구독 채널

## 로비

```text
/topic/lobby/rooms
```

## 대기방 공개 채널

```text
/topic/rooms/{roomId}
```

## 게임 공개 채널

```text
/topic/games/{gameId}
```

## 사용자 개인 응답

```text
/user/queue/replies
```

## 사용자 개인 게임 상태

```text
/user/queue/games/{gameId}
```

## 사용자 알림

```text
/user/queue/notifications
```

---

# 9. 로비 메시지

## LOBBY-WS-001 방 생성 이벤트

Subscribe:

```text
/topic/lobby/rooms
```

Event:

```json
{
  "eventType": "ROOM_CREATED",
  "payload": {
    "roomId": 10,
    "roomName": "초보방",
    "ownerNickname": "player1",
    "currentPlayers": 1,
    "maxPlayers": 4,
    "gameMode": "CLASSIC",
    "status": "WAITING"
  }
}
```

## LOBBY-WS-002 방 변경 이벤트

```json
{
  "eventType": "ROOM_UPDATED",
  "payload": {
    "roomId": 10,
    "currentPlayers": 2,
    "maxPlayers": 4,
    "status": "WAITING",
    "joinable": true
  }
}
```

## LOBBY-WS-003 방 제거 이벤트

```json
{
  "eventType": "ROOM_REMOVED",
  "payload": {
    "roomId": 10
  }
}
```

---

# 10. 대기방 요청

## ROOM-WS-001 방 입장 알림 연결

방 입장 자체는 REST로 처리할 수 있다.

입장 성공 후 구독:

```text
/topic/rooms/{roomId}
```

서버 이벤트:

```json
{
  "eventType": "ROOM_PLAYER_JOINED",
  "payload": {
    "roomId": 10,
    "player": {
      "userId": 2,
      "nickname": "player2",
      "avatarType": "DEFAULT_02",
      "seatOrder": 2,
      "readyStatus": "NOT_READY",
      "owner": false
    }
  }
}
```

## ROOM-WS-002 준비 상태 변경

Send:

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

Event:

```json
{
  "eventType": "ROOM_READY_CHANGED",
  "payload": {
    "roomId": 10,
    "userId": 2,
    "readyStatus": "READY"
  }
}
```

## ROOM-WS-003 게임 시작

Send:

```text
/app/rooms/{roomId}/start
```

Request:

```json
{
  "actionId": "uuid"
}
```

Public Event:

```json
{
  "eventType": "GAME_STARTED",
  "payload": {
    "roomId": 10,
    "gameId": 33,
    "gameMode": "CLASSIC",
    "startingUserId": 2,
    "gameVersion": 0
  }
}
```

각 사용자 개인 Event:

```json
{
  "eventType": "GAME_PRIVATE_INITIALIZED",
  "payload": {
    "gameId": 33,
    "rack": [
      {
        "tileId": "RED-7-A",
        "type": "NUMBER",
        "color": "RED",
        "number": 7
      }
    ]
  }
}
```

---

# 11. 게임 상태 공개 이벤트

## GAME-WS-001 공개 GameState

Subscribe:

```text
/topic/games/{gameId}
```

```json
{
  "eventType": "GAME_STATE_UPDATED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "gameMode": "CLASSIC",
    "gameStatus": "IN_PROGRESS",
    "currentTurnUserId": 2,
    "turnDeadlineAt": "2026-07-14T20:32:00+09:00",
    "remainingGameSeconds": null,
    "players": [
      {
        "userId": 1,
        "nickname": "player1",
        "avatarType": "DEFAULT_01",
        "seatOrder": 1,
        "rackCount": 8,
        "initialMeldCompleted": true,
        "connectionStatus": "CONNECTED"
      },
      {
        "userId": 2,
        "nickname": "player2",
        "avatarType": "DEFAULT_02",
        "seatOrder": 2,
        "rackCount": 10,
        "initialMeldCompleted": false,
        "connectionStatus": "CONNECTED"
      }
    ],
    "table": {
      "melds": [
        {
          "meldId": "meld-1",
          "meldType": "RUN",
          "tiles": [
            {
              "tileId": "RED-3-A",
              "type": "NUMBER",
              "color": "RED",
              "number": 3
            }
          ]
        }
      ]
    }
  }
}
```

공개 상태에는 어느 사용자 손패의 실제 타일 정보도 포함하면 안 된다.

---

# 12. 개인 GameState 이벤트

Subscribe:

```text
/user/queue/games/{gameId}
```

```json
{
  "eventType": "PRIVATE_GAME_STATE_UPDATED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "myUserId": 1,
    "rack": [
      {
        "tileId": "BLUE-7-A",
        "type": "NUMBER",
        "color": "BLUE",
        "number": 7
      }
    ],
    "initialMeldCompleted": true,
    "canAct": false,
    "turnDraftStatus": "NONE"
  }
}
```

---

# 13. TurnDraft 정책

1차 권장 구조:

```text
클라이언트에서 임시 배치 편집
→ 서버에는 확정 요청 시 최종 Draft 제출
```

장점:

- 드래그마다 서버 요청하지 않음
- 네트워크 부하 감소
- 다른 플레이어에게 미확정 배치 노출 없음

서버는 턴 시작 스냅샷과 제출된 최종 Draft를 비교해 검증한다.

---

# 14. 턴 확정

## TURN-WS-001 확정 요청

Send:

```text
/app/games/{gameId}/turn/commit
```

Request:

```json
{
  "actionId": "uuid",
  "gameId": 33,
  "gameVersion": 37,
  "actionType": "TURN_COMMIT",
  "payload": {
    "melds": [
      {
        "clientMeldId": "client-meld-1",
        "tiles": [
          "RED-3-A",
          "RED-4-B",
          "RED-5-A"
        ]
      }
    ]
  }
}
```

클라이언트는 자신의 최종 테이블 배치 결과를 tileId 목록으로 제출한다.

서버는 다음을 재검증한다.

- 현재 턴
- gameVersion
- tileId 존재
- 소유권
- 중복·유실
- 첫 등록
- 손패 최소 기여
- 조커
- 전체 테이블 유효성

## 성공 공개 이벤트

```json
{
  "eventType": "TURN_COMMITTED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "actionId": "uuid",
    "committedByUserId": 2,
    "nextTurnUserId": 3,
    "turnDeadlineAt": "2026-07-14T20:34:00+09:00",
    "table": {
      "melds": []
    },
    "players": [
      {
        "userId": 2,
        "rackCount": 7,
        "initialMeldCompleted": true
      }
    ]
  }
}
```

## 성공 개인 이벤트

요청자:

```json
{
  "eventType": "PRIVATE_RACK_UPDATED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "rack": []
  }
}
```

---

# 15. 턴 취소

## TURN-WS-002 취소 요청

턴 취소는 클라이언트 Draft만 폐기하는 구조라면 서버 요청이 없어도 된다.

다만 서버에 Draft를 저장하는 구조를 채택할 경우:

```text
/app/games/{gameId}/turn/cancel
```

1차 권장:

```text
클라이언트 로컬 Draft 폐기
→ 서버 확정 GameState 재적용
```

서버 상태는 바뀌지 않으므로 gameVersion도 증가하지 않는다.

---

# 16. 타일 뽑기

## TURN-WS-003 드로우 요청

Send:

```text
/app/games/{gameId}/turn/draw
```

Request:

```json
{
  "actionId": "uuid",
  "gameId": 33,
  "gameVersion": 37,
  "actionType": "DRAW_TILE",
  "payload": {}
}
```

공개 이벤트:

```json
{
  "eventType": "TILE_DRAWN",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "userId": 2,
    "rackCount": 11,
    "nextTurnUserId": 3,
    "turnDeadlineAt": "2026-07-14T20:34:00+09:00"
  }
}
```

개인 이벤트:

```json
{
  "eventType": "PRIVATE_TILE_DRAWN",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "tile": {
      "tileId": "BLACK-11-B",
      "type": "NUMBER",
      "color": "BLACK",
      "number": 11
    },
    "rack": []
  }
}
```

상대에게는 뽑은 타일 정보가 전송되면 안 된다.

---

# 17. PASS

## TURN-WS-004 PASS 요청

Send:

```text
/app/games/{gameId}/turn/pass
```

가능 조건:

```text
drawPoolEmpty = true
```

공개 이벤트:

```json
{
  "eventType": "TURN_PASSED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "userId": 2,
    "consecutivePassCount": 2,
    "nextTurnUserId": 3
  }
}
```

---

# 18. 시간 초과

시간 초과는 서버 스케줄러가 발생시킨다.

클라이언트가 `TIMEOUT` 요청을 보내지 않는다.

## TURN-WS-005 턴 시간 초과 이벤트

```json
{
  "eventType": "TURN_TIMED_OUT",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "timedOutUserId": 2,
    "draftRolledBack": true,
    "autoAction": "DRAW",
    "nextTurnUserId": 3,
    "turnDeadlineAt": "2026-07-14T20:34:00+09:00"
  }
}
```

개인 자동 드로우:

```json
{
  "eventType": "PRIVATE_TIMEOUT_DRAW",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "tile": {
      "tileId": "YELLOW-6-A",
      "type": "NUMBER",
      "color": "YELLOW",
      "number": 6
    },
    "message": "제한시간이 종료되어 배치 전 상태로 복원되고 타일 1개를 자동으로 뽑았습니다."
  }
}
```

공용 풀이 비어 있으면:

```text
autoAction = PASS
```

---

# 19. SPEED 전체 게임 시간 종료

서버 전체 게임 타이머가 0에 도달하면 발생한다.

현재 턴의 미확정 Draft는 롤백한다.

추가 드로우는 하지 않는다.

## SPEED-WS-001 종료 이벤트

```json
{
  "eventType": "SPEED_GAME_TIME_EXPIRED",
  "gameId": 44,
  "gameVersion": 72,
  "payload": {
    "draftRolledBack": true,
    "gameStatus": "FINISHED",
    "resultType": "WIN",
    "winnerUserId": 2,
    "scores": [
      {
        "userId": 2,
        "contributedTileScore": 72,
        "remainingTileScore": 20,
        "finalScore": 52
      }
    ]
  }
}
```

동점:

```json
{
  "resultType": "DRAW",
  "winnerUserId": null
}
```

---

# 20. 재접속

## RECONNECT-WS-001 연결 복귀 알림

클라이언트는 REST로 초기 복원 상태를 받은 뒤 WebSocket을 재구독한다.

Send:

```text
/app/games/{gameId}/reconnect
```

Request:

```json
{
  "actionId": "uuid",
  "gameId": 33,
  "gameVersion": 37,
  "actionType": "RECONNECT",
  "payload": {}
}
```

공개 이벤트:

```json
{
  "eventType": "PLAYER_RECONNECTED",
  "payload": {
    "userId": 1,
    "connectionStatus": "CONNECTED"
  }
}
```

개인 이벤트:

```json
{
  "eventType": "RECONNECT_CONFIRMED",
  "gameId": 33,
  "gameVersion": 38,
  "payload": {
    "latestVersion": 38,
    "staleDraftDiscarded": true
  }
}
```

## 연결 종료 공개 이벤트

```json
{
  "eventType": "PLAYER_DISCONNECTED",
  "payload": {
    "userId": 1,
    "connectionStatus": "DISCONNECTED"
  }
}
```

---

# 21. 게임 종료

## GAME-WS-002 일반 종료 이벤트

```json
{
  "eventType": "GAME_FINISHED",
  "gameId": 33,
  "gameVersion": 40,
  "payload": {
    "gameMode": "CLASSIC",
    "resultType": "WIN",
    "winnerUserId": 1,
    "endReason": "RACK_EMPTY",
    "ratingApplied": true,
    "players": [
      {
        "userId": 1,
        "resultType": "WIN",
        "remainingTileCount": 0,
        "remainingTileScore": 0,
        "ratingBefore": 1000,
        "ratingDelta": 18,
        "ratingAfter": 1018
      }
    ]
  }
}
```

DRAW:

```json
{
  "resultType": "DRAW",
  "winnerUserId": null
}
```

SPEED:

```text
ratingApplied = false
ratingBefore/ratingDelta/ratingAfter = null
```

---

# 22. 오류 이벤트

## ACTION_REJECTED

개인 채널:

```text
/user/queue/replies
```

### 예시

```json
{
  "eventType": "ACTION_REJECTED",
  "gameId": 33,
  "gameVersion": 37,
  "payload": {
    "actionId": "uuid",
    "errorCode": "INITIAL_MELD_SCORE_TOO_LOW",
    "message": "첫 등록 점수가 30점보다 낮습니다.",
    "recoverable": true,
    "refreshRequired": false
  }
}
```

STALE_GAME_VERSION:

```json
{
  "errorCode": "STALE_GAME_VERSION",
  "message": "게임 상태가 변경되었습니다. 최신 상태를 다시 불러옵니다.",
  "recoverable": true,
  "refreshRequired": true,
  "latestGameVersion": 38
}
```

---

# 23. 중복 요청 처리

동일 `actionId` 요청이 다시 들어오면 새 상태 변경을 실행하지 않는다.

권장 응답:

```json
{
  "eventType": "DUPLICATE_ACTION_ACK",
  "payload": {
    "actionId": "uuid",
    "alreadyProcessed": true,
    "resultingGameVersion": 38
  }
}
```

중복 요청이 처음 성공했는지 실패했는지 결과를 재사용할 수 있게 저장하는 것이 좋다.

---

# 24. gameVersion 정책

다음 성공 처리마다 증가한다.

- 게임 시작
- 턴 확정
- 타일 드로우
- PASS
- 시간 초과
- SPEED 전체 종료
- 게임 종료 상태 확정

증가하지 않는 항목:

- 손패 자동 정렬
- 클라이언트 Draft 편집
- 턴 취소
- 연결 상태만 바뀌는 단순 이벤트는 정책에 따라 별도 버전 가능

권장:

```text
게임 규칙 상태가 바뀔 때만 gameVersion 증가
```

---

# 25. 이벤트 순서

클라이언트는 `gameVersion`이 낮은 이벤트를 무시한다.

예:

```text
현재 버전 38
수신 이벤트 버전 37
→ 무시
```

현재 버전보다 2 이상 큰 이벤트를 받으면 상태 누락 가능성이 있으므로 REST 재조회 또는 전체 상태 요청을 수행한다.

---

# 26. Heartbeat

권장 STOMP Heartbeat:

```text
client outgoing: 10000ms
client incoming: 10000ms
```

연결 끊김 판정은 일시적 지연을 고려한다.

---

# 27. 서버 목적지 핸들러 초안

```text
/app/rooms/{roomId}/ready
/app/rooms/{roomId}/start

/app/games/{gameId}/turn/commit
/app/games/{gameId}/turn/draw
/app/games/{gameId}/turn/pass
/app/games/{gameId}/reconnect
```

1차에서는 턴 Draft 이동을 서버로 매번 보내지 않는다.

---

# 28. 프론트 상태 분리

Pinia 권장 Store:

```text
authStore
lobbyStore
roomStore
gamePublicStore
gamePrivateStore
turnDraftStore
connectionStore
```

중요:

```text
gamePublicStore
→ 공개 테이블과 상대 정보

gamePrivateStore
→ 내 손패

turnDraftStore
→ 미확정 로컬 편집
```

상대 손패 데이터 구조 자체를 public store에 두지 않는다.

---

# 29. 보안 요구사항

- destination만으로 권한을 신뢰하지 않는다.
- Principal과 game participant를 서버에서 비교한다.
- payload의 userId는 신뢰하지 않는다.
- 다른 gameId의 tileId를 사용할 수 없다.
- 개인 이벤트는 convertAndSendToUser 계열로 전송한다.
- 공개 이벤트에 개인 rack을 넣지 않는다.
- 로그에 전체 개인 손패를 남기지 않는 것을 권장한다.
- 운영 오류 응답에 내부 예외 메시지를 노출하지 않는다.

---

# 30. 필수 통합 테스트

## 연결

- 인증 성공
- 인증 실패
- 만료 토큰
- 중복 계정 연결

## 대기방

- 준비 상태 브로드캐스트
- 비방장 시작 차단
- 최소 2명
- 준비 미완료 차단

## 게임 시작

- 공개 상태와 개인 손패 분리
- 각 사용자에게 서로 다른 rack
- 총 106개 타일 무결성

## 턴 확정

- 성공 이벤트
- 실패 개인 이벤트
- 상대 손패 미노출
- gameVersion 증가
- 동일 actionId 중복 처리 방지

## 시간 초과

- 서버가 자동 발생
- Draft 롤백
- 자동 드로우 1회
- 개인 드로우 타일만 당사자에게 전송
- 다음 턴 전환

## SPEED

- 5분 전체 종료
- 현재 Draft 롤백
- 추가 드로우 없음
- 점수 계산
- 동점 DRAW
- 랭킹 미반영

## 재접속

- 공개 상태 복원
- 개인 손패 복원
- stale Draft 폐기
- 상대 연결 상태 변경

## 종료

- GAME_FINISHED 한 번만 발생
- 랭킹 중복 반영 없음
- 종료 후 행동 거부

---

# 31. 포트폴리오 설명 포인트

WebSocket 설계에서 다음을 핵심으로 설명한다.

1. 공개 채널과 개인 채널 분리
2. 상대 손패 원문 미전송
3. actionId를 통한 중복 요청 방지
4. gameVersion을 통한 오래된 요청 차단
5. 서버 기준 시간 초과
6. TurnDraft와 확정 GameState 분리
7. 시간 초과 시 스냅샷 롤백과 자동 드로우의 원자성
8. 재접속 시 최신 서버 상태 우선
9. CLASSIC과 SPEED의 동일 메시지 구조 재사용

---

# 32. 다음 문서

다음 단계:

1. 서버 GameState 모델
2. 규칙 엔진 클래스 설계
3. 테스트 케이스 상세표
4. 구현 단계별 작업지시서
5. 새 프로젝트 시작 프롬프트

---

# 33. Phase 7 실제 COMMIT 계약

## Client SEND

```text
/app/games/{gameId}/turn/commit
```

```json
{
  "actionId": "uuid",
  "gameVersion": 0,
  "melds": [
    { "clientMeldId": "uuid", "tileIds": ["RED-07-A", "RED-08-A", "RED-09-A"] }
  ]
}
```

클라이언트는 `meldType`과 `score`를 전송하지 않는다. 게임 참가자만 SEND할 수 있다.

## Public event

`/topic/games/{gameId}`의 `MELDS_COMMITTED` payload:

```text
gameId, gameVersion, committedByUserId, committedByRackCount
initialMeldCompleted, initialMeldScore, committedMeldIds
nextTurnUserId, nextTurnSeatOrder, turnNumber
currentTurnId, currentTurnStartedAt, turnDeadlineAt, consecutivePassCount
```

## Private state

`/user/queue/game-state`로 각 사용자에게 typed `tableMelds`와 자기 `myRack`만 보낸다. 공개 이벤트가 개인 Snapshot보다 먼저 도착할 수 있으므로, 클라이언트는 개인 sync 완료 후 pending Draft를 해소한다.

## Reply

`/user/queue/replies`에서 `COMMIT`의 accepted/rejected/replayed 결과를 기존 `GameCommandReply` 형식으로 받는다.

---

# 34. Phase 7 Second Review 전체 Candidate 계약

Section 33의 append-only `melds` 계약은 Second Review부터 다음 `tableMelds` 계약으로 대체한다.

```json
{
  "actionId": "uuid",
  "gameVersion": 12,
  "tableMelds": [
    { "meldId": "server-or-client-stable-id", "tileIds": ["RED-07-A", "RED-08-A", "RED-09-A"] },
    { "meldId": "another-id", "tileIds": ["BLUE-01-A", "BLUE-02-A", "BLUE-03-A"] }
  ]
}
```

`tableMelds`는 Commit 후 존재할 전체 Candidate Table이며 클라이언트는 type/score를 보내지 않는다. Public `MELDS_COMMITTED`는 `changedMeldIds`, `rackContributionCount`, `tableRecomposed`를 포함한다. typed Private Snapshot이 최종 권위이고 validation reject는 로컬 후보를 유지할 수 있지만 stale/reconnect는 최신 Snapshot으로 복구한다.
