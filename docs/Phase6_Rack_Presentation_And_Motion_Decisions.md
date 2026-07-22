# Phase 6 Rack Presentation 및 Motion 결정

## 1. 권위 상태와 표시 상태

`gameStore.privateState.myRack`은 서버가 확정한 권위 데이터다. 정렬과 drag는 이 배열 또는 타일 객체를 변경하지 않는다. 화면 계층은 다음만 소유한다.

- `serverOrderSnapshot`: 마지막 직접 REST/private snapshot의 tileId 순서
- `displayOrderIds`: 현재 보이는 tileId 순서
- `sortMode`: `SERVER`, `MANUAL`, `GROUP_777`, `RUN_789`
- drag 시작 순서와 현재 preview 순서
- sort lock과 Draw entering tileId

실제 렌더링 타일은 권위 Rack을 tileId map으로 만든 뒤 `displayOrderIds`로 투영한다. 따라서 화면 순서가 서버 payload, gameVersion, 다른 플레이어에게 영향을 주지 않는다.

## 2. 정렬 규칙

색상 우선순위는 `rackSorting.ts` 한 곳의 `RACK_COLOR_PRIORITY`로 고정한다.

```text
RED → BLUE → YELLOW → BLACK
```

- 777: number → color → 마지막 서버 순서 index
- 789: color → number → 마지막 서버 순서 index
- Joker: 항상 일반 타일 뒤, Joker끼리는 마지막 서버 순서
- comparator가 같은 타일은 서버 순서 index로 stable하게 결정

정렬 함수는 입력 배열을 복제해 반환하며 tileId 보존 invariant를 별도 함수로 검사할 수 있다.

## 3. 원래 순서와 이벤트 병합

원래 순서는 최초 mount 당시 순서가 아니라 마지막 직접 서버 Rack 수신 순서다.

- REST/private snapshot: `serverOrderSnapshot` 전체를 새 Rack 순서로 교체하고 SERVER 모드로 복원
- 공개 `GAME_STATE_UPDATED`: private Rack revision이 없으므로 표시 순서를 건드리지 않음
- 정상 private Rack 추가: 기존 표시 순서에서 살아 있는 tileId를 유지하고 새 tileId를 서버 추가 순서로 병합
- 제거: 더 이상 권위 Rack에 없는 tileId만 표시 배열에서 제거
- 777/789 모드: 병합 후 현재 자동 정렬을 재적용
- MANUAL/SERVER 모드: 기존 표시 순서를 유지하면서 새 타일을 뒤에 추가

이 정책은 public event가 private event보다 먼저 또는 나중에 와도 기존 로컬 정렬을 보존한다.

## 4. Draw 모션 확정

Draw 버튼 click 자체는 모션 근거가 아니다. 다음 두 증거가 같은 version 흐름에서 상관될 때만 진입 모션을 확정한다.

1. 내 seat의 정상 `TILE_DRAWN`
2. private Rack에서 실제로 추가된 tileId

공개 이벤트와 private 이벤트의 도착 순서는 고정하지 않는다. store가 양쪽 pending 정보를 보관해 두 증거가 모이면 `drawMotionTileIds`와 revision을 한 번 올린다. duplicate/old version은 기존 version guard와 완료 version 기록으로 무시한다.

따라서 요청 직후, 거절, 상대 Draw, 공개 이벤트만 도착한 상태에는 내 새 타일 진입 모션이 없다.

## 5. Drag 결정

- Pointer Events만 사용해 mouse/touch/pen 입력 경로를 통일
- 시작 타일에서 pointer capture
- pointermove에서 Rack 타일 중심 좌표와 가장 가까운 index를 계산
- 순서 계산은 `moveTileId` 순수 함수 사용
- pointerup이 Rack 경계 안이면 확정하고 `MANUAL`로 전환
- Rack 밖, pointercancel, 유효하지 않은 target이면 시작 순서 복원
- pointermove 중 store deep clone이나 서버 호출 없음

## 6. Motion 정책

Motion Token은 `game-motion.css`에만 둔다.

- 정렬 move: 270ms
- drag/enter 기본 이동: 180ms
- Draw 이동: 340ms
- Draw settle: 160ms
- 정렬 중 빠른 두 번째 입력: 버튼 잠금으로 무시하는 정책 A

정렬 재배치는 Vue `TransitionGroup`의 move class를 사용한다. Draw는 opacity만 바꾸지 않고 우측 Draw 영역 방향의 `translate`, `rotate`, `scale`을 함께 사용한다. `prefers-reduced-motion: reduce`에서는 duration을 1ms로 낮추고 Draw transform animation 대신 outline 피드백을 준다.

## 7. 테마와 Asset Registry

- `game-theme.css`: 색, shadow, 타일/Rack 외형 토큰
- `game-motion.css`: duration, easing, keyframes
- `rummikub-inspired.css`: 레이아웃과 responsive 규칙
- `gameAssets.ts`: board/rack/tile/avatar/draw/pass 교체 경로

기본 registry는 외부 이미지가 없어도 CSS로 완성되도록 빈 경로를 허용한다. 라이선스가 확인된 자산은 registry 값만 바꾸거나 해당 컴포넌트 prop으로 주입할 수 있다.

## 8. 계약과 범위 경계

- Draw/PASS는 기존 `gameStore.drawTile()`과 `gameStore.passTurn()`만 호출
- DTO, endpoint, WebSocket destination, backend, DB를 변경하지 않음
- 상대 seat에는 서버의 공개 `rackCount`만 전달
- TurnDraft, 타일 제출, Meld 편집은 중앙 안내 영역으로만 표시하고 Phase 7에 남김
- 전역 사이트 UI는 게임 route에서만 숨기고 로그인·로비 등 기존 화면은 유지

