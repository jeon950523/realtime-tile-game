# Phase 7 Third Review Completion Report

## 1. 결과

Phase 7의 전체 Candidate Table Commit 구조를 유지하면서 교차 플레이어 Meld 연장, 명령 Pending 복구, Working Table UI 단순화, Joker 기여 점수를 보완했다.

- Player B의 Rack 타일을 Player A가 처음 만든 Meld에 붙일 수 있다. 기존 Meld의 `createdBy`는 보존되고 새 Meld만 현재 Commit 사용자를 생성자로 기록한다.
- `CONNECTED`와 게임 명령 준비 완료를 분리했다. Public Game Event, Private State, Reply 구독과 최초 Private State가 준비되기 전에는 Draw/PASS/Commit을 보내지 않는다.
- Commit/Draw/PASS에 9초 응답 제한을 두고 accepted Reply 뒤에는 1.5초만 Private State를 기다린다. 이후에는 REST 권위 상태를 조회하며 자동 재전송하지 않는다.
- publish 예외, rejected Reply, stale/not-current-turn, STOMP 오류, WebSocket 종료, route 이탈, active game 변경, timeout에서 Pending timer를 정리한다.
- 타일 아래 조작 버튼과 `다음 Meld와 병합` 버튼, 그 전용 props/emits/CSS를 제거했다. 같은 Meld/다른 Meld/빈 작업판/Rack 사이의 Drag & Drop은 유지했다.
- `전체 Rack`은 새 Meld이면서 모든 타일이 현재 Rack 출신일 때만 표시한다.
- RUN/GROUP 검증이 계산한 위치별 실질 숫자를 제출 점수에도 재사용한다. Rack Joker가 RUN 중간·양끝 또는 GROUP에서 0점으로 보이지 않는다.

## 2. 원인 분석

### 시크릿/신규 세션 Pending

기존 Frontend는 WebSocket의 일반 `CONNECTED`만 행동 준비 조건으로 사용했다. Reply 또는 Private State가 유실되거나 서버 예외로 Reply가 발행되지 않으면 `commandInProgress`를 해제할 timeout과 권위 상태 복구 경계가 없었다. accepted Reply를 받은 뒤에도 Private State가 오지 않으면 무기한 대기했다.

Backend Controller는 `BusinessException` 중심으로 Reply를 만들었고 Domain/Application의 예기치 않은 runtime 예외가 Reply 경계를 벗어날 가능성이 있었다. 이제 stack trace와 action/game/user 문맥은 서버 로그에 남기고, 클라이언트에는 같은 actionId의 일반 `INTERNAL_SERVER_ERROR` 거절만 반환한다.

### 교차 플레이어 Meld 실패

`GameTile.commitToTable`이 Rack 타일 소유자와 대상 Meld 최초 생성자가 같아야 한다고 강제했다. 이 검사는 정상적인 다음 플레이어의 기존 Meld 연장을 막았다. 해당 검사만 제거했으며 Candidate reconciliation의 동일 게임, 현재 요청자 Rack 소유, POOL/상대 Rack 차단, 중복 및 기존 TABLE 누락 검증은 유지했다.

### Working Table 레이아웃

각 타일 아래의 다섯 개 버튼과 Meld 병합 버튼이 타일 수에 비례해 높이와 폭을 늘렸고 Drag & Drop 계약과 기능이 중복됐다. 버튼 DOM과 전용 계약을 제거하고 drag source, 삽입 위치, 잠금 상태만 시각화했다.

## 3. 자동 검증

- Backend targeted: `mvnw.cmd -Dtest=GameMessageControllerTest,GameTurnCommitIntegrationTest test`
  - 20 tests, failures 0, errors 0, skipped 0
- Backend full/build: `mvnw.cmd clean verify`
  - 51 test classes, 299 tests, failures 0, errors 0, skipped 0
  - executable JAR packaging PASS
- Frontend: `npm run check`
  - 23 test files, 238 tests, failures 0
  - TypeScript `vue-tsc --build` PASS
  - Vite production build PASS

## 4. Runtime 상태

자동 테스트에서는 신규 세션, 필수 구독 전송 차단, Reply/State 유실, disconnect, timeout 후 반영/미반영 복구를 재현했다. 실제 로그인 계정 두 개를 사용하는 일반/시크릿 브라우저 게임 Commit은 사용자 데이터와 진행 중 게임을 임의 변경하지 않기 위해 이 작업에서 실행하지 않았다. `Phase7_Third_Review_Runtime_Guide.md` 절차로 사용자 검증이 필요하다.

## 5. 남은 제한

- 기존 Phase 7 정책대로 TABLE Joker가 들어 있는 기존 Meld는 재조합 잠금 상태다.
- 기존 Table 전체 재조합은 지원하지만 Joker 회수/재사용, 게임 종료, timeout scheduler는 이번 범위가 아니다.
- per-tile 버튼을 제거했으므로 세밀한 타일 재조합은 현재 pointer Drag & Drop 중심이다. Undo, Cancel, Commit, `전체 Rack`은 키보드 버튼으로 유지되지만 타일 단위 키보드 재배치 패턴은 향후 접근성 보완 대상이다.

