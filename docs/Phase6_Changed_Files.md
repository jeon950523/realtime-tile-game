# Phase 6 변경 파일

기준 전체본: `E:\rumi\codex\phase0717-20-32-phase5_5C-final-clean-source`

## 제품 코드 및 테스트

### 생성

- `frontend/src/types/rackPresentation.ts` — Rack 표시 모드와 presentation 상태 타입
- `frontend/src/domain/game/rackSorting.ts` — 777/789 stable 정렬, 원래 순서 병합, tileId 이동 순수 함수
- `frontend/src/composables/game/useRackPresentation.ts` — 서버 Rack과 화면 순서 분리, 정렬·드래그·Draw 모션 상태
- `frontend/src/config/gameAssets.ts` — 교체 가능한 게임 Asset Registry
- `frontend/src/components/game/GameTile.vue` — 접근 가능한 타일과 Pointer 입력 표면
- `frontend/src/components/game/TileRack.vue` — 2단 Rack, TransitionGroup, 자유 드래그
- `frontend/src/components/game/RackToolbar.vue` — 789/777/원래 순서 컨트롤
- `frontend/src/components/game/PlayerSeat.vue` — 상대 좌석, 공개 Rack 개수, 현재 턴 표시
- `frontend/src/components/game/TurnActionControl.vue` — 기존 Draw/PASS 조건을 표시하는 행동 컨트롤
- `frontend/src/components/game/GameDebugPanel.vue` — 공개 상태·버전 진단 패널
- `frontend/src/components/game/GameBoard.vue` — 게임 화면 레이아웃 조합
- `frontend/src/styles/game/game-theme.css` — 색상·크기·외형 토큰
- `frontend/src/styles/game/game-motion.css` — 정렬·드래그·Draw Motion Token과 reduced-motion
- `frontend/src/styles/game/rummikub-inspired.css` — 참고 이미지 기반 게임 화면 스타일
- `frontend/src/__tests__/RackSorting.spec.ts` — 정렬·drag 순수 함수 검증
- `frontend/src/__tests__/RackPresentation.spec.ts` — server/display 분리, 이벤트 병합, 모션 상태 검증

### 수정

- `frontend/src/views/GameView.vue` — Phase 5 카드형 화면을 Phase 6 게임 보드 조합으로 교체
- `frontend/src/stores/game.ts` — DTO를 바꾸지 않고 Rack 동기화 출처와 정상 Draw 모션 상관관계 신호 추가
- `frontend/src/main.ts` — 게임 테마·모션·화면 CSS 진입점 추가
- `frontend/src/assets/main.css` — 기존 Phase 5 게임 화면 전용 스타일 제거
- `frontend/src/__tests__/GameView.spec.ts` — 로컬 정렬, 상대 턴, Draw 진입, 4인 배치 회귀 테스트 추가
- `frontend/src/__tests__/GameStore.spec.ts` — 공개/비공개 이벤트 도착 순서와 중복 Draw 모션 회귀 테스트 추가

제품 코드 변경은 총 22개 파일이며 `backend/**`, API DTO, WebSocket destination, DB 및 migration 변경은 0개다.

## 검증 증거와 문서 산출물

- `Phase6_Runtime_Evidence/Phase6_1920x1080.png`
- `Phase6_Runtime_Evidence/Phase6_Draw_Motion_1920x1080.png`
- `Phase6_Runtime_Evidence/Phase6_1280x720.png`
- `Phase6_Documents/Phase6_Changed_Files.md`
- `Phase6_Documents/Phase6_Completion_Report.md`
- `Phase6_Documents/Phase6_Direct_Verification_Guide.md`
- `Phase6_Documents/Phase6_Test_Case_Traceability.md`
- `Phase6_Documents/Phase6_Rack_Presentation_And_Motion_Decisions.md`

위 검증 증거와 문서는 전달용 산출물이며 제품 Patch ZIP에는 포함하지 않는다. 문서 ZIP에는 문서 5종만 포함한다.

## 산출물에서 제외한 항목

- `frontend/node_modules/**`
- `frontend/dist/**`
- `backend/target/**`
- 브라우저 검증용 임시 H2 설정, 계정 seed script, `frontend/.env.local`
- 전체 프로젝트 사본

