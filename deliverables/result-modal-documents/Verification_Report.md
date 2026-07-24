# Phase 7 — Rack Exhaustion Result Modal 검증 보고서

- 검증일: 2026-07-23 (Asia/Seoul)
- 기준: 작업 시작 시점의 현재 작업 디렉터리 최신 전체 소스
- 자동 검증 판정: **CONDITIONAL PASS**

## 1. 현재 문제의 실제 원인

Backend는 Rack 0 Commit 직후 Game을 `FINISHED`, Room을 `CLOSED`로 만들고 Active Game을 해제한 뒤 `GAME_TERMINATED`를 정상 발행하고 있었다.

문제는 Frontend `GameView.vue`의 `terminalRevision` watcher가 종료 사유를 구분하지 않고 다음 작업을 즉시 수행한 데 있었다.

```text
Active Game Context 정리 감지
→ Room 로컬 상태 정리
→ Room 목록 재조회
→ Lobby Realtime 연결
→ /lobby 이동
```

따라서 `RACK_EXHAUSTED` 승자와 패자가 결과를 확인하기 전에 게임 Route가 사라졌다.

## 2. 변경 전/후 호출 흐름

### 변경 전

```text
Rack 0 Commit
→ Backend Game FINISHED / Room CLOSED / Active Game 해제
→ GAME_TERMINATED
→ Frontend Active Game Context 정리
→ terminalRevision 증가
→ 즉시 /lobby 이동
```

### 변경 후

```text
Rack 0 Commit
→ Backend Game FINISHED / Room CLOSED / Active Game 해제
→ GAME_TERMINATED
→ 종료 직전 privateState.players에서 승자 Snapshot 생성
→ Active Game / Pending Command / Realtime Context 정리
→ 현재 /games/{gameId} Route에 결과 전용 배경과 강제 확인 Modal 표시
→ 사용자가 '로비로 나가기' 클릭
→ Room 로컬 상태 정리 / 목록 갱신 / Lobby 연결
→ /lobby 이동 성공
→ 결과 Snapshot 제거
```

`PLAYER_LEFT`와 `PLAYER_FORFEIT`는 기존 즉시 Lobby 이동 흐름을 유지한다.

## 3. 변경 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/types/game.ts` | 메모리 전용 `GameResultSnapshot` 계약 추가 |
| `frontend/src/stores/game.ts` | 종료 전 승자 Snapshot 생성, Active Context와 결과 상태 분리, 명시적 acknowledge Action |
| `frontend/src/views/GameView.vue` | RACK_EXHAUSTED 자동 이동 중단, 결과 전용 배경, 버튼 기반 Lobby 이동, 이중 클릭 잠금 |
| `frontend/src/components/game/GameResultModal.vue` | 승자/패자 문구, Nickname/Seat/Seat 색상/종료 사유, 닫기 불가 Modal |
| `frontend/src/__tests__/GameStore.spec.ts` | 승자·패자 Snapshot, 중복 이벤트, reply 순서, acknowledge/logout 정리 검증 |
| `frontend/src/__tests__/GameView.spec.ts` | Route 유지, Modal, 입력 차단, ESC/배경 클릭, 이중 클릭, 새로고침, 기존 Forfeit 흐름 검증 |

## 4. Backend 변경 유무

이번 Result Modal 작업에서 Backend 운영 코드와 Backend 테스트는 변경하지 않았다.

기존 Rack 소진 검증 테스트는 현재 작업 디렉터리의 기준선 그대로 유지했고 전체 Backend 회귀만 재실행했다.

- Game 즉시 `FINISHED`: 유지
- Room 즉시 `CLOSED`: 유지
- Active Game 즉시 해제: 유지
- `GAME_TERMINATED` 발행: 유지
- 결과 확인을 위한 Room/DB 상태 지연: 추가하지 않음
- Debug Endpoint/DB 컬럼: 추가하지 않음

## 5. 종료 Snapshot 수명과 정리 시점

`terminalResult`는 Pinia 메모리에만 존재한다.

생성 시점:

```text
RACK_EXHAUSTED GAME_TERMINATED 수신
→ 기존 privateState 참조
→ 승자/본인 정보 계산
→ terminalResult 저장
→ Active Game Context 정리
```

정리 시점:

- 사용자가 `로비로 나가기`를 눌러 Lobby Route 이동에 성공한 뒤
- Logout의 `clearGameState()`
- 새 게임 상태를 정상 로드할 때

localStorage, sessionStorage, IndexedDB, Backend DB에는 저장하지 않는다. 새로고침하면 새 Store가 생성되고 서버 Active Game이 없으므로 `/lobby`로 이동한다.

## 6. 승자 Nickname 결정 방법

`GAME_TERMINATED.winnerUserId`를 종료 직전 `privateState.publicState.players[].userId`와 연결한다.

같은 참가자 객체에서 다음을 Snapshot으로 보관한다.

- `winnerNickname`
- `winnerSeatOrder`
- `myUserId`
- `didIWin`

승자를 찾지 못한 예외 상황에서는 `승자가 결정되었습니다.`를 표시한다. 정상 Payload 테스트에서는 실제 `owner` 또는 `guest` Nickname과 Seat가 표시됨을 검증했다.

Seat 색상은 새 팔레트를 만들지 않고 기존 Meld 수정자 표시의 `meldModifierStyle()` 계약을 재사용한다.

## 7. 중복 이벤트 및 이중 클릭 방어

중복 종료 이벤트:

- 기존 `terminalGameId` idempotency guard 유지
- 같은 gameId 재수신 시 Snapshot을 다시 만들거나 덮어쓰지 않음
- `terminalRevision`을 다시 증가시키지 않음
- Lobby 연결이나 Route 이동을 자동 실행하지 않음

Command Reply 순서:

- 종료 이벤트가 먼저 오면 Context 정리 후 늦은 Reply는 무시
- Accepted Commit Reply가 먼저 와도 종료 전 `privateState`가 남아 있어 후속 종료 이벤트에서 정확한 Snapshot 생성

이중 클릭:

- `resultExitInProgress`를 await 이전에 동기적으로 설정
- `loadRooms`, `connectLobby`, `router.replace` 각각 1회만 실행
- 중간 실패 시 `finally`에서 잠금을 해제
- Route 이동 성공 전에는 Snapshot을 제거하지 않아 Modal이 사라진 채 갇히지 않음

## 8. 게임 입력과 Modal 닫기 정책

종료 시 운영 `privateState`를 즉시 제거하고 결과 전용 화면만 렌더링한다. 따라서 다음 UI가 DOM에서 제거되어 Frontend 입력이 차단된다.

- Tile Drag
- Draw / Pass
- Commit / Undo / Cancel
- Rack 정렬
- 게임 포기 및 나가기

Modal에는 X와 Cancel 버튼이 없고, 배경 클릭 또는 ESC 처리 코드도 없다. 허용 버튼은 `로비로 나가기` 하나다.

결과 상태에서는 `게임 상태를 복구하는 중입니다.` 대신 결과 전용 배경을 표시한다.

## 9. 실제 실행한 명령과 결과

```powershell
cd .\frontend
npm.cmd run type-check
npm.cmd run test:unit -- --run src/__tests__/GameStore.spec.ts src/__tests__/GameView.spec.ts
npm.cmd run check

cd ..\backend
.\mvnw.cmd clean test
```

전체 결과:

| 구분 | Tests run | Failures/Failed | Errors | Skipped | 결과 |
|---|---:|---:|---:|---:|---|
| Backend | 349 | 0 | 0 | 0 | BUILD SUCCESS |
| Frontend Vitest | 394 | 0 | 해당 없음 | 해당 없음 | 34 files passed |
| Vue Type Check | 해당 없음 | 0 | 0 | 해당 없음 | 성공 |
| Production Build | 해당 없음 | 0 | 0 | 해당 없음 | Vite build 성공 |

표적 테스트는 Store 22개 + GameView 19개, 총 41개가 통과했다.

초기 표적 검증에서 발생한 실패는 다음처럼 분류하고 수정했다.

1. Pinia 반응형 Proxy에 `structuredClone`을 적용한 테스트 Fixture 오류
2. Realtime Client를 생성하지 않은 테스트에서 disconnect 호출을 기대한 잘못된 Fixture 기대값

두 실패 모두 운영 코드 결함이나 새 UX 계약 문제가 아니었으며, 기대값 완화 없이 검증 대상에 맞게 Fixture를 수정했다.

## 10. Runtime 미검증 항목

자동 Docker/Kubernetes 재배포는 지시대로 실행하지 않았다. 다음은 실제 Runtime에서 아직 확인하지 않았다.

- 실제 브라우저 A/B가 동시에 각각 결과 Modal을 유지하는 장면
- 실제 STOMP를 통한 양쪽 `GAME_TERMINATED` 수신
- 실제 MySQL에서 Room CLOSED 및 Active Game 해제 상태
- 실제 브라우저 배경 클릭/ESC/키보드 포커스 UX
- 한쪽만 버튼을 누를 때 다른 브라우저 Modal이 계속 유지되는지
- 결과 Modal 상태에서 실제 브라우저 새로고침 후 Lobby 이동
- 운영 프록시 환경에서 Lobby 재연결

## 11. 최종 판정

**CONDITIONAL PASS**

코드 구현, Backend 전체 회귀, Frontend 전체 Check는 모두 통과했다. 작업지시서 기준에 따라 실제 브라우저 2개 Runtime 시나리오까지 통과하면 **PASS / FINAL**로 승격할 수 있다.
