# Phase 3 Completion Report

## 1. 기준

```text
최신 기준: phase0715-11-04-phase2-final-game.zip
작업: Phase 3 — Lobby And Waiting Room Foundation
작성일: 2026-07-15
```

## 2. 최종 구현 범위

- 2·3·4인 공개 CLASSIC 방 생성
- 방 목록·빠른 후보·상세·active-room REST
- 입장·이탈·정원·좌석·방장 위임
- 준비 상태와 게임 시작 조건 검증
- V2 rooms/room_players Migration
- Commit 이후 Lobby·Room Event
- STOMP CONNECT JWT 인증
- Lobby 인증과 Room Membership 인가
- actionId 중복 처리
- LobbyView·RoomCreateModal·WaitingRoomView
- 새로고침 active room 복구

## 3. Backend

```text
기존 테스트: 156
신규 Phase 3 테스트: 39
전체 테스트: 195
Failures: 0
Errors: 0
Skipped: 0
BUILD: SUCCESS
```

실행 명령:

```text
mvn clean test
```

검수 환경:

```text
실행 JVM: Java 21.0.10
컴파일 대상: Java 17 release
```

## 4. Frontend

```text
기존 테스트: 34
신규 Phase 3 테스트: 22
전체 Vitest: 56
TypeScript: 통과
Production Build: 통과
npm ci: 성공
npm audit 설치 결과: 취약점 0건
```

실행 명령:

```text
npm ci
npm run check
```

## 5. DB

```text
V2 Migration: 생성
H2 MySQL Mode: 적용 성공
Flyway: V1 + V2 검증 성공
ddl-auto validate: 통과
실제 MySQL 8.4: 사용자 직접 검증 필요
```

V1은 수정하지 않았고 `games`, `game_players`는 생성하지 않았다.

## 6. Room

```text
2인 생성: 통과
3인 생성: 통과
4인 생성: 통과
CLASSIC 고정: 적용
SPEED 생성: INVALID_GAME_MODE
비공개 방: PRIVATE_ROOM_NOT_SUPPORTED
중복 참가: 차단
정원 경쟁: 정확히 1명 성공
Seat 재사용: 가장 작은 빈 번호
방장 위임: joinedAt ASC, id ASC
마지막 이탈: CLOSED
Active Room 복구 API: 구현
```

## 7. 동시성·이벤트

```text
User Lock: PESSIMISTIC_WRITE
Room Lock: PESSIMISTIC_WRITE
Lock 순서: User → Room
동일 사용자 동시 생성: 1개만 성공
생성·입장 경쟁: Membership 1개
마지막 자리 경쟁: 1명만 성공
방장 이탈·입장 경쟁: 활성 방장 1명
Commit 후 Event: 1회
Rollback Event: 0회
```

Portable Partial Unique Index 대신 Transaction Lock을 선택했다. 다중 서버와 DB 이중 Unique 보장은 후속 확장 한계다.

## 8. WebSocket

```text
익명 Health: 유지
JWT CONNECT: 구현
명시적 잘못된 JWT: 연결 거부
Lobby Subscribe: 인증 필요
Room Subscribe: 활성 Membership 필요
READY/START SEND: Membership 필요
현재 ACTIVE 상태: 매 보호 메시지 재검증
BLOCKED/DELETED: 즉시 거부
JWT 만료: 보호 메시지 거부
Token URL Query: 사용하지 않음
Token 로그: 사용하지 않음
```

기존 `/ws`, `/app/system.health.ping`, `/topic/system.health` 계약은 유지했다.

## 9. Action ID

```text
Key: userId + actionId
TTL: 10분
최대 크기: 10,000
동일 요청: Domain 실행 없음
공개 Event: 중복 없음
개인 Reply: 기존 결과 재전송
잘못된 UUID: INVALID_ROOM_ACTION_ID
```

서버 재시작 후 중복 이력 복구는 제외다.

## 10. Start Boundary

```text
비방장: 차단
최소 인원: 2명
방장 포함 전원 READY: 필요
성공 Reply: ROOM_START_REQUEST_ACCEPTED
Game 생성: 없음
gameId: 없음
RoomStatus PLAYING 전환: 없음
GAME_STARTED Event: 없음
```

실제 시작 Transaction은 Phase 4로 이관했다.

## 11. Frontend

- 인증 사용자 기본 이동을 `/lobby`로 변경했다.
- active room이 있으면 `/rooms/{id}`로 복구한다.
- 방 목록 REST 초기 조회와 STOMP Upsert/Remove를 구현했다.
- 재연결 후 전체 목록을 다시 조회한다.
- 방 생성·입장·나가기·READY·START 중복 요청을 차단한다.
- Access Token은 기존 Pinia 메모리에서만 사용한다.
- STOMP CONNECT Header 외 Token 전송 위치를 추가하지 않았다.
- 대기방 로그아웃은 방 나가기 성공 후에만 수행한다.

## 12. Regression

```text
Phase 1 순수 규칙 엔진: 유지
Phase 2 인증·프로필: 유지
회원가입·로그인·reissue·프로필·로그아웃: 회귀 통과
Health REST: 통과
Health WebSocket 실제 왕복: 통과
pom.xml: 변경 없음
frontend/package-lock.json: 변경 없음
```

## 13. 변경하지 않은 범위

```text
games Table: 없음
game_players Table: 없음
GameState: 없음
GameSession: 없음
타일 셔플·분배: 없음
선 플레이어 결정: 없음
게임 화면: 없음
SPEED 방: 없음
Redis·다중 서버: 없음
```

## 14. 알려진 한계

- 실제 MySQL 8.4 V2 Migration은 이 환경에서 실행하지 못했다.
- Chrome 일반 창·시크릿 창 2계정 실시간 동작은 사용자 확인이 필요하다.
- 대기방 장기 방치 자동 정리가 없다.
- actionId 저장소는 JVM 메모리 기반이다.
- Simple Broker 기반 단일 서버다.
- WebSocket 오류는 안전한 code 중심이며 Phase 4에서 게임 Command 오류 계약과 통합할 수 있다.

## 15. 사용자 직접 검증

`docs/Phase3_Direct_Verification_Guide.md`에 따라 확인한다.

핵심:

```text
Java 17 clean test 195
MySQL V2
2계정 방 생성·입장
실시간 인원·READY
방장 위임
마지막 이탈 제거
F5 active-room 복구
BLOCKED 보호 SEND 차단
Console Error 0
```

## 16. 최종 판정

```text
Phase 3 정적·자동 검증: 통과
사용자 Java 17·MySQL 8.4·Chrome 2계정 직접 검증: 필요
Phase 4 진행: 사용자 직접 검증 전 보류
```
