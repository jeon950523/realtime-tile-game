# 실시간 타일 보드게임 REST API 명세 v1

작성 기준: 2026-07-14  
문서 상태: 1차 MVP + 랭킹·상대 전적 확장 고려  
Base URL 예시:

```text
/api
```

인증 방식:

```text
Access Token: Authorization: Bearer <token>
Refresh Token: HttpOnly Cookie 권장
```

연결 문서:

- `Realtime_Tile_Game_Project_Planning_v1.md`
- `Realtime_Tile_Game_Rules_Spec_v1.md`
- `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `Realtime_Tile_Game_SRS_v1.md`
- `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `Realtime_Tile_Game_ERD_Table_Spec_v1.md`

---

# 1. REST와 WebSocket 책임 분리

## REST 사용 영역

- 회원가입
- 로그인
- 토큰 재발급
- 로그아웃
- 내 프로필
- 프로필 수정
- 로비 초기 방 목록
- 방 생성
- 방 상세 초기 조회
- 진행 중 게임 존재 여부
- 게임 초기 복원 상태 조회
- 내 전적
- 특정 상대 전적
- 랭킹 조회
- 최근 게임 결과 조회

## WebSocket 사용 영역

- 로비 방 목록 변경 이벤트
- 대기방 입장·퇴장
- 준비 상태
- 게임 시작
- 턴 행동
- 타일 드로우
- 턴 확정·취소
- 시간 초과
- 재접속 상태 동기화
- 게임 종료

---

# 2. 공통 응답 형식

## 성공

```json
{
  "success": true,
  "data": {},
  "timestamp": "2026-07-14T20:20:00+09:00"
}
```

## 실패

```json
{
  "success": false,
  "error": {
    "code": "ROOM_FULL",
    "message": "방의 정원이 가득 찼습니다.",
    "fieldErrors": []
  },
  "timestamp": "2026-07-14T20:20:00+09:00"
}
```

## 필드 오류

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "입력값을 확인해주세요.",
    "fieldErrors": [
      {
        "field": "email",
        "message": "올바른 이메일 형식이 아닙니다."
      }
    ]
  }
}
```

---

# 3. 인증 API

## AUTH-001 회원가입

```http
POST /api/auth/register
```

### Request

```json
{
  "email": "user@example.com",
  "password": "qwer1234!",
  "passwordConfirm": "qwer1234!",
  "nickname": "player1"
}
```

### Response 201

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "player1",
    "profileSetupRequired": true
  }
}
```

### 오류

```text
EMAIL_ALREADY_EXISTS
NICKNAME_ALREADY_EXISTS
PASSWORD_CONFIRM_MISMATCH
VALIDATION_FAILED
```

---

## AUTH-002 로그인

```http
POST /api/auth/login
```

### Request

```json
{
  "email": "user@example.com",
  "password": "qwer1234!"
}
```

### Response 200

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt-access-token",
    "expiresIn": 1800,
    "user": {
      "userId": 1,
      "nickname": "player1",
      "avatarType": "DEFAULT_01",
      "ratingScore": 1000
    },
    "redirect": {
      "type": "LOBBY",
      "roomId": null,
      "gameId": null
    }
  }
}
```

진행 중 게임이 있으면:

```json
{
  "redirect": {
    "type": "RECONNECT",
    "roomId": 10,
    "gameId": 33
  }
}
```

### 오류

```text
INVALID_CREDENTIALS
USER_BLOCKED
USER_DELETED
```

---

## AUTH-003 토큰 재발급

```http
POST /api/auth/reissue
```

Refresh Token은 HttpOnly Cookie에서 읽는다.

### Response 200

```json
{
  "success": true,
  "data": {
    "accessToken": "new-access-token",
    "expiresIn": 1800
  }
}
```

### 오류

```text
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
```

---

## AUTH-004 로그아웃

```http
POST /api/auth/logout
```

### Response 204

Refresh Token Cookie를 제거한다.

---

# 4. 프로필 API

## PROFILE-001 내 프로필 조회

```http
GET /api/me
```

### Response

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "player1",
    "avatarType": "DEFAULT_01",
    "ratingScore": 1042,
    "classicRecord": {
      "wins": 18,
      "losses": 11,
      "draws": 3,
      "totalGames": 32
    },
    "speedRecord": {
      "wins": 9,
      "losses": 6,
      "draws": 2,
      "totalGames": 17
    },
    "activeSession": {
      "roomId": null,
      "gameId": null,
      "status": null
    }
  }
}
```

---

## PROFILE-002 프로필 수정

```http
PATCH /api/me/profile
```

### Request

```json
{
  "nickname": "newNickname",
  "avatarType": "DEFAULT_03"
}
```

### Response

```json
{
  "success": true,
  "data": {
    "nickname": "newNickname",
    "avatarType": "DEFAULT_03"
  }
}
```

### 오류

```text
NICKNAME_ALREADY_EXISTS
INVALID_AVATAR_TYPE
VALIDATION_FAILED
```

---

# 5. 로비 API

## LOBBY-001 방 목록 조회

```http
GET /api/rooms?status=WAITING&gameMode=CLASSIC&page=0&size=20
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "roomId": 10,
        "roomName": "초보방",
        "ownerNickname": "player1",
        "currentPlayers": 2,
        "maxPlayers": 4,
        "gameMode": "CLASSIC",
        "turnTimeLimitSeconds": 120,
        "status": "WAITING",
        "joinable": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 정책

- 기본 조회는 `WAITING`
- 게임 중인 방 표시가 필요하면 별도 필터 허용
- 로비 WebSocket 연결 후 변경 이벤트로 갱신

---

## LOBBY-002 빠른 입장 후보 조회

```http
GET /api/rooms/quick-match?gameMode=CLASSIC
```

### Response

```json
{
  "success": true,
  "data": {
    "roomId": 10,
    "available": true
  }
}
```

후보가 없으면:

```json
{
  "roomId": null,
  "available": false
}
```

---

# 6. 방 API

## ROOM-001 방 생성

```http
POST /api/rooms
```

### Request: CLASSIC

```json
{
  "roomName": "초보방",
  "maxPlayers": 4,
  "gameMode": "CLASSIC",
  "turnTimeLimitSeconds": 120,
  "isPublic": true
}
```

### Request: SPEED

```json
{
  "roomName": "5분 스피드",
  "maxPlayers": 4,
  "gameMode": "SPEED",
  "turnTimeLimitSeconds": 20,
  "gameTimeLimitSeconds": 300,
  "isPublic": true
}
```

### Response 201

```json
{
  "success": true,
  "data": {
    "roomId": 10,
    "roomName": "초보방",
    "ownerUserId": 1,
    "maxPlayers": 4,
    "gameMode": "CLASSIC",
    "status": "WAITING"
  }
}
```

### 서버 보정

모드에 따라 서버가 허용 범위를 검증한다.

```text
CLASSIC
- gameTimeLimitSeconds = null

SPEED
- gameTimeLimitSeconds = 300
- turnTimeLimitSeconds 기본 20
```

### 오류

```text
USER_ALREADY_IN_ROOM
INVALID_MAX_PLAYERS
INVALID_GAME_MODE
INVALID_TIME_LIMIT
VALIDATION_FAILED
```

---

## ROOM-002 방 상세 조회

```http
GET /api/rooms/{roomId}
```

### Response

```json
{
  "success": true,
  "data": {
    "roomId": 10,
    "roomName": "초보방",
    "ownerUserId": 1,
    "ownerNickname": "player1",
    "maxPlayers": 4,
    "gameMode": "CLASSIC",
    "turnTimeLimitSeconds": 120,
    "gameTimeLimitSeconds": null,
    "status": "WAITING",
    "players": [
      {
        "userId": 1,
        "nickname": "player1",
        "avatarType": "DEFAULT_01",
        "seatOrder": 1,
        "readyStatus": "READY",
        "owner": true
      }
    ]
  }
}
```

---

## ROOM-003 방 입장

```http
POST /api/rooms/{roomId}/join
```

### Request

없음.

### Response

```json
{
  "success": true,
  "data": {
    "roomId": 10,
    "seatOrder": 2,
    "joined": true
  }
}
```

### 오류

```text
ROOM_NOT_FOUND
ROOM_FULL
ROOM_ALREADY_PLAYING
USER_ALREADY_IN_ROOM
FORBIDDEN
```

> 입장 후 참가자 목록 실시간 변경은 WebSocket 이벤트로 전파한다.

---

## ROOM-004 방 나가기

```http
DELETE /api/rooms/{roomId}/members/me
```

### Response 204

### 정책

- 대기방에서만 가능
- 방장 이탈 시 서버가 다음 방장을 위임
- 게임 중 이탈은 별도 게임 포기 정책이 필요하므로 1차에서는 차단 가능

---

# 7. 진행 중 게임 및 재접속 API

## GAME-001 진행 중 세션 조회

```http
GET /api/me/active-session
```

### Response: 없음

```json
{
  "success": true,
  "data": {
    "active": false,
    "roomId": null,
    "gameId": null,
    "status": null
  }
}
```

### Response: 있음

```json
{
  "success": true,
  "data": {
    "active": true,
    "roomId": 10,
    "gameId": 33,
    "status": "IN_PROGRESS"
  }
}
```

---

## GAME-002 재접속 초기 상태 조회

```http
GET /api/games/{gameId}/reconnect
```

### Response

```json
{
  "success": true,
  "data": {
    "gameId": 33,
    "roomId": 10,
    "gameMode": "CLASSIC",
    "gameStatus": "IN_PROGRESS",
    "gameVersion": 37,
    "currentTurnUserId": 2,
    "turnDeadlineAt": "2026-07-14T20:22:00+09:00",
    "remainingGameSeconds": null,
    "me": {
      "userId": 1,
      "nickname": "player1",
      "initialMeldCompleted": true,
      "rack": [
        {
          "tileId": "RED-7-A",
          "type": "NUMBER",
          "color": "RED",
          "number": 7
        }
      ]
    },
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
      "melds": []
    }
  }
}
```

### 보안

- 참가자만 조회 가능
- `me.rack`에 자신의 손패만 포함
- 상대 손패 원문 금지

---

# 8. 게임 결과 API

## RESULT-001 게임 결과 조회

```http
GET /api/games/{gameId}/result
```

### Response

```json
{
  "success": true,
  "data": {
    "gameId": 33,
    "gameMode": "CLASSIC",
    "resultType": "WIN",
    "winnerUserId": 1,
    "winnerNickname": "player1",
    "endReason": "RACK_EMPTY",
    "durationSeconds": 1722,
    "ratingApplied": true,
    "players": [
      {
        "userId": 1,
        "nickname": "player1",
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
  "winnerUserId": null,
  "winnerNickname": null
}
```

---

## RESULT-002 내 최근 게임 목록

```http
GET /api/me/games?gameMode=CLASSIC&page=0&size=20
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "gameId": 33,
        "gameMode": "CLASSIC",
        "resultType": "WIN",
        "ratingDelta": 18,
        "playedAt": "2026-07-14T20:30:00+09:00",
        "participants": [
          {
            "userId": 2,
            "nickname": "player2"
          }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1
  }
}
```

---

# 9. 랭킹 API

## RANKING-001 랭킹 목록

```http
GET /api/rankings?page=0&size=50
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "rank": 1,
        "userId": 10,
        "nickname": "ranker",
        "avatarType": "DEFAULT_04",
        "ratingScore": 1324
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 100
  }
}
```

### 정렬

```text
rating_score DESC
id ASC
```

---

## RANKING-002 내 랭킹 위치

```http
GET /api/rankings/me
```

### Response

```json
{
  "success": true,
  "data": {
    "rank": 24,
    "ratingScore": 1042,
    "totalRankedUsers": 100
  }
}
```

---

## RANKING-003 내 랭킹 이력

```http
GET /api/me/rating-history?page=0&size=20
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "gameId": 33,
        "ratingBefore": 1000,
        "ratingDelta": 18,
        "ratingAfter": 1018,
        "opponentAverageRating": 1100.0,
        "expectedScore": 0.359935,
        "actualScore": 1.0,
        "createdAt": "2026-07-14T20:30:00+09:00"
      }
    ]
  }
}
```

---

# 10. 상대 전적 API

## RECORD-001 특정 상대 전적 조회

```http
GET /api/me/head-to-head/{opponentUserId}
```

### Response

```json
{
  "success": true,
  "data": {
    "opponent": {
      "userId": 2,
      "nickname": "player2",
      "avatarType": "DEFAULT_02",
      "ratingScore": 980
    },
    "overall": {
      "wins": 5,
      "losses": 2,
      "draws": 1,
      "totalGames": 8,
      "winRate": 62.5
    },
    "classic": {
      "wins": 4,
      "losses": 2,
      "draws": 1,
      "totalGames": 7
    },
    "speed": {
      "wins": 1,
      "losses": 0,
      "draws": 0,
      "totalGames": 1
    },
    "lastPlayedAt": "2026-07-14T19:40:00+09:00"
  }
}
```

---

## RECORD-002 상대 전적 목록

```http
GET /api/me/head-to-head?page=0&size=20&sort=lastPlayedAt,desc
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "opponentUserId": 2,
        "opponentNickname": "player2",
        "opponentRatingScore": 980,
        "wins": 5,
        "losses": 2,
        "draws": 1,
        "totalGames": 8,
        "winRate": 62.5,
        "lastPlayedAt": "2026-07-14T19:40:00+09:00"
      }
    ]
  }
}
```

---

## RECORD-003 특정 상대와 최근 경기

```http
GET /api/me/head-to-head/{opponentUserId}/games?page=0&size=20
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "gameId": 33,
        "gameMode": "CLASSIC",
        "myResult": "WIN",
        "opponentResult": "LOSS",
        "myRatingDelta": 18,
        "playedAt": "2026-07-14T20:30:00+09:00"
      }
    ]
  }
}
```

---

# 11. 사용자 검색 API

상대 전적 화면이나 프로필 조회에서 사용한다.

## USER-001 닉네임 검색

```http
GET /api/users/search?nickname=player
```

### Response

```json
{
  "success": true,
  "data": [
    {
      "userId": 2,
      "nickname": "player2",
      "avatarType": "DEFAULT_02",
      "ratingScore": 980
    }
  ]
}
```

### 제한

- 최대 10건
- 이메일 비노출
- 삭제·차단 사용자 제외

---

# 12. 권한 표

| API | 비로그인 | 로그인 | 방 참가자 | 게임 참가자 |
|---|---:|---:|---:|---:|
| 회원가입 | O | O | O | O |
| 로그인 | O | O | O | O |
| 내 프로필 | X | O | O | O |
| 방 목록 | X | O | O | O |
| 방 생성 | X | O | O | O |
| 방 상세 | X | O | O | O |
| 재접속 상태 | X | X | X | O |
| 게임 결과 | X | O | O | O |
| 랭킹 | X | O | O | O |
| 상대 전적 | X | O | O | O |

게임 결과 조회는 참가자에게 우선 허용하며, 공개 전적 정책은 후속 결정할 수 있다.

---

# 13. HTTP 상태 코드

```text
200 OK
201 Created
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

권장 매핑:

```text
EMAIL_ALREADY_EXISTS → 409
ROOM_FULL → 409
STALE_GAME_VERSION → 409
VALIDATION_FAILED → 400
FORBIDDEN → 403
GAME_NOT_FOUND → 404
```

---

# 14. 페이징 정책

요청:

```text
page: 0부터 시작
size: 기본 20
최대 size: 100
sort: field,direction
```

응답:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

---

# 15. 보안 요구사항

- 비밀번호 응답 금지
- Refresh Token 응답 본문 노출 금지 권장
- 상대 손패 REST 노출 금지
- gameId만으로 참가자 데이터 조회 금지
- userId를 요청에서 받아도 인증 사용자와 권한 재검증
- 닉네임 검색에서 이메일 비노출
- 민감 오류에 내부 스택트레이스 비노출
- 로그에 Access Token, Refresh Token, 비밀번호 기록 금지

---

# 16. 멱등성과 중복 방지

REST 생성·변경 API 중 중복 가능성이 큰 요청은 선택적으로 `Idempotency-Key` 헤더를 지원할 수 있다.

```http
Idempotency-Key: uuid
```

우선 적용 후보:

- 방 생성
- 방 입장
- 로그아웃
- 프로필 수정

게임 행동은 WebSocket의 `actionId`로 중복 방지한다.

---

# 17. 구현 순서

## 1단계

- 회원가입
- 로그인
- 토큰 재발급
- 로그아웃
- 내 프로필

## 2단계

- 방 목록
- 방 생성
- 방 상세
- 방 입장
- 방 나가기

## 3단계

- 진행 중 세션
- 재접속 초기 상태
- 게임 결과

## 4단계

- 최근 게임
- 랭킹
- 랭킹 이력
- 상대 전적

---

# 18. API 테스트 필수 범위

## 인증

- 정상 회원가입
- 이메일 중복
- 닉네임 중복
- 비밀번호 불일치
- 로그인 성공·실패
- 만료 Refresh Token
- 로그아웃 후 재발급 차단

## 방

- 2인 방 생성
- 3인 방 생성
- 4인 방 생성
- 잘못된 최대 인원
- 정원 초과
- 게임 중 입장
- 중복 입장
- 방장 이탈 위임

## 재접속

- 참가자 정상 조회
- 비참가자 403
- 자신의 손패만 응답
- 최신 gameVersion
- SPEED 남은 전체시간 포함

## 랭킹

- 점수 내림차순
- 동점 시 id 오름차순
- 내 위치 정확
- SPEED 경기 후 이력 없음

## 상대 전적

- 사용자 A/B 관점 반대 결과
- CLASSIC/SPEED 구분
- DRAW 반영
- 최근 대전일
- 원본 게임 결과와 일치

---

# 19. 다음 문서

다음 단계:

1. WebSocket 메시지 명세
2. 서버 GameState 모델
3. 규칙 엔진 클래스 설계
4. 테스트 케이스 상세표
5. 구현 작업지시서
