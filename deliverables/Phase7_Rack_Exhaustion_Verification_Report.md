# Phase 7 — Rack Exhaustion Game Completion 검수 보고서

- 검수일: 2026-07-23 (Asia/Seoul)
- 대상: 실시간 루미큐브형 타일 게임의 Rack 소진 정상 승리 루프
- 최종 판정: **CONDITIONAL PASS**

## 1. 결론

현재 운영 구현은 유효한 Meld Commit으로 현재 플레이어의 Rack이 0이 되면 턴을 진행하지 않고 즉시 정상 종료한다.

감사 중 운영 코드 결함은 발견되지 않았다. 따라서 운영 규칙, Debug Endpoint, 우회 로직은 변경하지 않았고 결정적 DB Fixture를 사용하는 통합 테스트와 Frontend 단위/컴포넌트 테스트만 보강했다.

자동 검증은 전부 통과했다.

| 구분 | 테스트 수 | 실패 | 오류 | 스킵 | 부가 검증 |
|---|---:|---:|---:|---:|---|
| Backend | 349 | 0 | 0 | 0 | clean compile, H2/Flyway 통합 테스트 |
| Frontend | 387 | 0 | 0 | 해당 없음 | vue-tsc, Vite production build 성공 |
| 합계 | 736 | 0 | 0 | 0 | 전체 게이트 성공 |

## 2. 실제 실행한 명령

PowerShell 환경에서 실행 파일 확장자와 Execution Policy를 고려해 요청 명령과 동등한 Windows wrapper를 사용했다.

```powershell
cd backend
.\mvnw.cmd '-Dtest=GameTurnCommitIntegrationTest,GameMessageControllerTest' test
.\mvnw.cmd clean test

cd ..\frontend
npm.cmd run test:unit -- --run src/__tests__/GameStore.spec.ts src/__tests__/GameView.spec.ts
npm.cmd run check
```

참고:

- 최초 Maven 표적 실행은 sandbox 네트워크 제한으로 Maven Central 접근이 거부되어 승인된 네트워크 환경에서 재실행했다.
- `npm` PowerShell shim은 로컬 Execution Policy로 거부되어 동일한 npm CLI인 `npm.cmd`를 사용했다.
- sandbox 안의 Vitest/esbuild 설정 탐색은 상위 디렉터리 읽기 제한에 걸려 승인된 실행 환경에서 재실행했다.

## 3. 운영 구현 감사 결과

감사한 핵심 운영 흐름:

1. `GameTurnCommitService.commit`이 턴/버전/소유권/배치와 Meld 규칙을 먼저 검증한다.
2. 검증 성공 후 후보 Table을 원자적으로 반영한다.
3. 반영된 엔티티 상태에서 현재 플레이어의 Rack 수를 계산한다.
4. Rack이 0이면 `completeByRackExhaustion`으로 분기한다.
5. 승자/패자 상태 지정, 모든 RoomPlayer의 leave 처리, Room close, Game finish가 같은 트랜잭션에서 수행된다.
6. 이 분기에서는 `advanceAfterMeld`를 호출하지 않는다.
7. 커밋 후 `GAME_TERMINATED`, `ROOM_CLOSED`, `ROOM_REMOVED`가 발행된다.

운영 코드 변경: **없음**

Production Debug Endpoint 추가: **없음**

운영 규칙 완화: **없음**

## 4. Rack 0 종료 시 실제 상태 전이

| 항목 | 마지막 Commit 직전 | 유효 Meld 반영 후 종료 상태 | 검증 결과 |
|---|---|---|---|
| 현재 플레이어 Rack | 결정적 Fixture의 6개 타일 | 0 | PASS |
| Table | 마지막 Meld 미반영 | 유효 Meld 2개/6타일 반영 | PASS |
| Game.status | `IN_PROGRESS` | `FINISHED` | PASS |
| terminationReason | `null` | `RACK_EXHAUSTED` | PASS |
| winnerUserId | `null` | 현재 플레이어 userId | PASS |
| 현재 플레이어 상태 | `ACTIVE` | `WINNER` | PASS |
| 다른 플레이어 상태 | `ACTIVE` | `LOSER` | PASS |
| turnNumber | N | N, 증가 없음 | PASS |
| currentTurnId | T | T, 변경 없음 | PASS |
| Game.version | V | V+1 | PASS |
| Room.status | `PLAYING` | `CLOSED` | PASS |
| RoomPlayer.leftAt | 모두 `null` | 모두 기록됨 | PASS |
| 양 사용자 Active Game | 존재 | 모두 없음 | PASS |
| 게임 공개 종료 이벤트 | 없음 | `GAME_TERMINATED` 정확히 1회 | PASS |
| 종료 payload | 해당 없음 | reason/winner IDs 존재, exited IDs null | PASS |
| Room 이벤트 | 없음 | `ROOM_CLOSED` 후 `ROOM_REMOVED` | PASS |
| 다음 턴 진행 | 가능 상태 | 실행되지 않음 | PASS |

정상 Rack 소진 종료 시 별도의 `MELDS_COMMITTED` 공개 이벤트 대신 최종 상태를 대표하는 `GAME_TERMINATED`가 1회 발행된다.

## 5. 회귀 검증

| 시나리오 | 검증 내용 | 결과 |
|---|---|---|
| A. Rack 잔여 | `IN_PROGRESS`, winner/reason 없음, 다음 턴/turnNumber 정상 진행, 종료/Room 이벤트 없음 | PASS |
| B. 마지막 타일 전부 제출 + 무효 Meld | `INVALID_MELD`, Rack/Table/Version 원상태, winner 없음, 이벤트 없음 | PASS |
| C. 종료 후 명령 | Draw, Pass, Commit 모두 `GAME_NOT_IN_PROGRESS`, 타일/이벤트 불변 | PASS |
| D. 같은 version의 동시 마지막 Commit | 성공 1건, 거부 1건, version 1회 증가, winner 1명, 종료/Room 이벤트 각 1회 | PASS |
| D-보완. 같은 actionId 재전송 | Controller replay store가 서비스 호출 1회 및 committed version 재응답 | 기존 테스트 PASS |

## 6. Frontend 검증

| 항목 | 검증 | 결과 |
|---|---|---|
| 내가 승자 | `모든 Rack 타일을 소진하여 승리했습니다.` 정확 문자열 | PASS |
| 상대가 승자 | `상대 플레이어가 모든 Rack 타일을 소진하여 게임이 종료되었습니다.` 정확 문자열 | PASS |
| 종료 Context | activeGameId/privateState 제거, terminalRevision 1 증가 | PASS |
| 종료 내비게이션 | Room 정리, 방 목록 갱신, Lobby 연결, `/lobby` 이동 | PASS |
| 기존 종료 사유 | `PLAYER_LEFT`, `PLAYER_FORFEIT` 기존 테스트 유지 | PASS |

## 7. Runtime으로 아직 확인하지 못한 항목

다음은 자동화된 H2/Spring 통합 테스트와 jsdom/Vitest 범위 밖이다.

- 실제 MySQL을 사용하는 배포 환경에서의 동일 종료 트랜잭션
- 실제 브라우저 2개와 실제 STOMP 연결에서 두 사용자 모두가 종료 이벤트를 수신하는 장면
- 네트워크 지연/재연결 중 종료 이벤트와 command reply 도착 순서에 대한 실브라우저 관찰
- 실제 화면에서 종료 문구가 사용자에게 표시된 뒤 로비로 이동하는 UX 육안 확인
- 운영 브로커/프록시를 통과한 `GAME_TERMINATED`, `ROOM_CLOSED`, `ROOM_REMOVED` 전달 순서

## 8. 최종 판정

**CONDITIONAL PASS**

코드, 트랜잭션, 상태 전이, 중복 방지, Frontend Context 및 전체 회귀 게이트는 모두 통과했으므로 기능 구현 자체는 완료 상태다. 다만 실시간 기능의 최종 FINAL 선언에는 위 Runtime 항목 중 최소한 실제 브라우저 2개 + 실제 Backend/MySQL/STOMP 환경의 1회 smoke test를 권고한다. 해당 smoke test까지 통과하면 **PASS / FINAL close 가능**으로 승격할 수 있다.
