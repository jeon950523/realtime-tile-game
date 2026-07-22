# Phase 6 직접 검증 가이드

## 1. 자동 검증

```powershell
cd "E:\rumi\codex\phase0717-20-32-phase5_5C-final-clean-source\frontend"
npm ci
npm run check
```

`npm run check`는 TypeScript, Vitest, Vite production build를 순서대로 실행한다. 세 단계가 모두 종료 코드 0인지 따로 확인한다.

## 2. 로컬 실행

프로젝트의 기존 backend 실행 방법으로 API와 WebSocket을 먼저 실행한다. 이어서 frontend 환경 변수의 API/WS 주소가 해당 backend를 가리키게 한 뒤 다음을 실행한다.

```powershell
cd "E:\rumi\codex\phase0717-20-32-phase5_5C-final-clean-source\frontend"
npm run dev
```

두 계정을 만들고 같은 방에 입장시켜 READY 후 게임을 시작한다. 각 계정의 실제 Rack 상세가 자기 화면에서만 보이는지 확인한다.

## 3. 단일 계정 Rack 검증

1. 게임에 입장해 내 Rack 개수와 실제 타일 수가 같은지 확인한다.
2. `777`을 누르고 숫자 → 색상 순서, stable tie, Joker 마지막을 확인한다.
3. 모션이 끝나기 전에 다른 정렬 버튼이 잠기는지 확인한다.
4. `789`를 누르고 색상 → 숫자 순서를 확인한다.
5. `원래 순서`를 눌러 마지막 서버 수신 순서가 복원되는지 확인한다.
6. 첫 타일을 마지막 타일 위치로 drag해 실제 순서와 상태 문구가 `직접 정렬`로 바뀌는지 확인한다.
7. 타일을 Rack 밖에 놓아 직전 유효 순서가 복원되는지 확인한다.
8. 새로고침 후 서버 순서와 `원래 순서` 모드로 복원되는지 확인한다.

각 단계에서 DevTools Network와 게임 정보의 Version을 함께 본다. 정렬·drag 자체는 REST 요청, WebSocket SEND, gameVersion 변경을 만들면 안 된다.

## 4. 정상 Draw 모션 검증

1. 내 턴, Pool > 0, CONNECTED 상태에서 Draw를 누른다.
2. 요청 중 중복 클릭이 막히는지 확인한다.
3. 서버가 정상 `TILE_DRAWN`을 보낸 뒤에만 내 Rack이 한 장 증가하는지 확인한다.
4. 새 tileId 하나만 우측 Draw 영역 방향에서 Rack으로 들어오는지 확인한다.
5. 모션 종료 뒤 진입 표시 class/state가 제거되는지 확인한다.
6. 턴과 Pool이 서버 상태에 맞게 바뀌는지 확인한다.

Draw 요청 직후 서버 확정 전, 거절 응답, 중복/오래된 이벤트에는 진입 모션이 없어야 한다.

## 5. 상대 턴과 비공개 계약 검증

1. A가 Draw해 B의 턴으로 넘긴다.
2. A 화면에서 777/789/원래 순서와 drag가 계속 가능한지 확인한다.
3. 이 동작 동안 gameVersion과 Pool이 바뀌지 않는지 확인한다.
4. B로 로그인해 A의 Rack이 `15개` 같은 개수만 보이는지 확인한다.
5. 상대 좌석 DOM·접근성 이름·Network payload에 A의 tileId/숫자/색상이 표시되지 않는지 확인한다.
6. B 자신의 Rack 상세와 정렬은 정상인지 확인한다.

## 6. 회귀 검증

- Pool > 0: Draw 표시
- Pool == 0: PASS 표시
- 내 턴 아님: 행동 비활성, Rack 정렬 활성
- command in progress: 행동 중복 클릭 차단
- 로그아웃/재로그인/재접속 후 snapshot 복구
- 현재 턴 좌석 ring과 countdown 표시
- 오류 메시지는 `role="alert"`, 연결·정렬 상태는 `role="status"`
- 키보드 focus-visible과 `prefers-reduced-motion: reduce`

## 7. 화면 크기

다음 viewport에서 document overflow와 핵심 UI의 bounding box를 확인하고 스크린샷을 남긴다.

- 1920×1080
- 1366×768
- 1280×720

toolbar, Rack, Draw/PASS가 서로 겹치거나 viewport 밖으로 나가지 않아야 한다. 14장과 Draw 후 15장이 모두 2단 Rack 안에서 보여야 한다.

## 8. 이번 실행의 증거

- `Phase6_Runtime_Evidence/Phase6_1920x1080.png`
- `Phase6_Runtime_Evidence/Phase6_Draw_Motion_1920x1080.png`
- `Phase6_Runtime_Evidence/Phase6_1280x720.png`

실제 실행 결과와 미실행 항목은 `Phase6_Completion_Report.md`를 기준으로 한다.

