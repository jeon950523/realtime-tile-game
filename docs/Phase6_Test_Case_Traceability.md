# Phase 6 테스트 추적표

상태 표기:

- `PASS-AUTO`: 2026-07-17 `npm run check`에서 통과
- `PASS-RUNTIME`: 실제 브라우저와 REST/WebSocket 런타임에서 확인
- `AUTO-ONLY`: 자동 테스트만 실행하고 해당 조건의 실서버 runtime은 만들지 않음

## 정렬

| ID | 요구사항 | 자동 검증 | Runtime | 결과 |
|---|---|---|---|---|
| SORT-001 | 777 숫자→색상 | `RackSorting.spec.ts` | A 14장, A 15장 상대 턴 | PASS-AUTO, PASS-RUNTIME |
| SORT-002 | 789 색상→숫자 | `RackSorting.spec.ts` | A와 B Rack | PASS-AUTO, PASS-RUNTIME |
| SORT-003 | 복제 타일 stable 순서 | `RackSorting.spec.ts` | B의 중복 숫자 관찰 | PASS-AUTO, PASS-RUNTIME |
| SORT-004 | Joker 마지막 | `RackSorting.spec.ts` | B의 `JOKER-B`가 789 마지막 | PASS-AUTO, PASS-RUNTIME |
| SORT-005 | 정렬 버튼이 서버 요청을 만들지 않음 | `GameView.spec.ts`의 store publish 비호출 | gameVersion 1, Pool 77 유지 | PASS-AUTO, PASS-RUNTIME |
| SORT-006 | 다른 플레이어 턴에도 정렬 가능 | `GameView.spec.ts` | A가 상대 턴일 때 777 실행 | PASS-AUTO, PASS-RUNTIME |
| SORT-007 | 마지막 서버 수신 순서 복원 | `RackSorting.spec.ts`, `RackPresentation.spec.ts` | 원래 순서와 reload | PASS-AUTO, PASS-RUNTIME |
| SORT-008 | tileId 집합·유일성 보존 | `RackSorting.spec.ts` | 정렬·drag 전후 tileId 비교 | PASS-AUTO, PASS-RUNTIME |
| SORT-009 | 777/789/drag가 store Rack을 직접 변경하지 않음 | `RackPresentation.spec.ts` | snapshot reload에서 서버 순서 복원 | PASS-AUTO, PASS-RUNTIME |
| SORT-010 | 정렬 후 public gameVersion 불변 | `GameView.spec.ts` | A 상대 턴과 B에서 Version 1 유지 | PASS-AUTO, PASS-RUNTIME |
| SORT-011 | 공개 이벤트만으로 표시 순서를 초기화하지 않음 | `RackPresentation.spec.ts` | Draw 전 공개 상태 변화 중 로컬 순서 유지 | PASS-AUTO, PASS-RUNTIME |
| SORT-012 | 내 TILE_DRAWN 후 기존 순서와 새 tileId 완전 병합 | `RackPresentation.spec.ts` | `BLACK-05-B` 추가, 14→15 | PASS-AUTO, PASS-RUNTIME |
| SORT-013 | 777 모드에서 Draw 후 777 재적용 | `RackPresentation.spec.ts` | 상태 로직은 자동 검증 | AUTO-ONLY |
| SORT-014 | 789 모드에서 Draw 후 789 재적용 | `RackPresentation.spec.ts` | 상태 로직은 자동 검증 | AUTO-ONLY |
| SORT-015 | 직접 REST snapshot 후 새 서버 순서가 원래 순서 | `RackPresentation.spec.ts` | 페이지 reload 후 서버 순서·SERVER 모드 | PASS-AUTO, PASS-RUNTIME |

## Drag와 Motion

| ID | 요구사항 | 자동 검증 | Runtime | 결과 |
|---|---|---|---|---|
| DRAG-001 | 첫 타일을 마지막 위치로 이동 | `RackSorting.spec.ts` | 실제 Pointer drag | PASS-AUTO, PASS-RUNTIME |
| DRAG-002 | 마지막 타일을 첫 위치로 이동 | `RackSorting.spec.ts` | 순수 함수 경계 검증 | PASS-AUTO |
| DRAG-003 | 같은 위치 drop은 불변 | `RackSorting.spec.ts` | 순수 함수 검증 | PASS-AUTO |
| DRAG-004 | Rack 밖 drop은 마지막 유효 순서 복원 | `RackPresentation.spec.ts` | 중앙 보드로 Pointer drag | PASS-AUTO, PASS-RUNTIME |
| DRAG-005 | drag 후 sortMode MANUAL | `RackPresentation.spec.ts` | 상태 문구 `현재: 직접 정렬` | PASS-AUTO, PASS-RUNTIME |
| MOTION-001 | 정렬 move transition/FLIP 상태 | `RackPresentation.spec.ts`, class assertion | 정렬 중 toolbar 3개 잠금 | PASS-AUTO, PASS-RUNTIME |
| MOTION-002 | Reduced Motion에서도 최종 순서 정상 | `RackPresentation.spec.ts`, CSS token | 실 OS reduced-motion은 미전환 | PASS-AUTO |

## Draw Motion

| ID | 요구사항 | 자동 검증 | Runtime | 결과 |
|---|---|---|---|---|
| DRAW-MOTION-001 | TILE_DRAWN 전 진입 모션 없음 | `RackPresentation.spec.ts` | 요청 전 entering 0 | PASS-AUTO, PASS-RUNTIME |
| DRAW-MOTION-002 | 정상 TILE_DRAWN의 새 tileId만 진입 | `RackPresentation.spec.ts`, `GameView.spec.ts`, `GameStore.spec.ts` | `BLACK-05-B` 하나, 두 animation 실행 | PASS-AUTO, PASS-RUNTIME |
| DRAW-MOTION-003 | 중복·오래된 TILE_DRAWN은 중복 모션 없음 | `RackPresentation.spec.ts`, `GameStore.spec.ts` | 중복 실이벤트 주입은 안 함 | AUTO-ONLY |

## 회귀·구조·접근성

| 검증 항목 | 증거 | 결과 |
|---|---|---|
| 기존 Draw/PASS 조건 | 기존+보강 `GameView.spec.ts`, `GameStore.spec.ts` | PASS-AUTO |
| private Rack 비공개 | 기존 store/view tests, B 화면 상대는 `Rack 15개`만 표시 | PASS-AUTO, PASS-RUNTIME |
| 4인 상대 배치 | `GameView.spec.ts` | PASS-AUTO |
| TypeScript | `vue-tsc --build` | PASS-AUTO |
| 전체 frontend 회귀 | Vitest 16 files, 120 tests | PASS-AUTO |
| Production build | Vite, 140 modules transformed | PASS-AUTO |
| Console | error 0, warning 0, alert 0 | PASS-RUNTIME |
| Responsive | 1920×1080, 1366×768, 1280×720 overflow false | PASS-RUNTIME |
| 연결·로그아웃·재로그인 | CONNECTED, A logout, B login | PASS-RUNTIME |
| backend/API/WS/DB 소스 무변경 | Patch 대상에 `backend/**` 없음 | PASS |

## 실행하지 않은 실브라우저 조건

- Pool을 0으로 소진한 PASS runtime
- 서버 거절 Draw runtime
- 중복/오래된 WebSocket 이벤트의 수동 주입
- 실 3인·4인 게임
- OS reduced-motion 설정을 켠 실제 애니메이션 관찰

위 항목은 실행했다고 주장하지 않으며, 현재 결과 표에서 `AUTO-ONLY` 또는 자동 회귀 범위로 명시했다.

