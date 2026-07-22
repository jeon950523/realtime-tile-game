# Phase 7 Second Review 테스트 추적표

## Backend Recomposition

`GameTurnCommitIntegrationTest`의 6개 묶음 테스트가 `RECOMPOSE-001~024`를 추적한다.

| 범위 | 검증 |
|---|---|
| RECOMPOSE-001~004 | 기존 Meld 연장, 서버 type/score 재계산 |
| RECOMPOSE-005~008 | 하나의 Meld 분리와 두 최종 shape 영속화 |
| RECOMPOSE-009~012 | Meld 병합, orphan 제거, UNIQUE position 보존 |
| RECOMPOSE-013~016 | Meld 순서 변경과 baseline 타일 교차 이동 |
| RECOMPOSE-017~021 | 누락·중복·foreign·POOL·Rack 기여 없음 거부 |
| RECOMPOSE-022~024 | 첫 등록 baseline lock, 실패 rollback, 유효 append |

## Frontend

| 범위 | 개수 | 검증 |
|---|---:|---|
| LAYOUT-001~006 | 6 | 21~30장 열/행/크기 계산 |
| TURN-UX-001~004 | 4 | 내 턴 강조와 상대 턴 해제 |
| STATUS-001~005 | 5 | 첫 등록/완료/이번 제출 표시 |
| WORK-001~014 | 14 | 복제, 이동, 분리, 병합, 직접 추가, invalid intermediate |
| RECOVER-001~008 | 8 | Cancel, turn end, stale, reconnect, reply/snapshot 순서 |
| COMMIT-FE-011~015 | 5 | 전체 Candidate contract와 event/reconciliation |

기존 Phase 0~7 테스트를 함께 실행해 회귀를 확인했다.

## 실행 결과

- Backend: `./mvnw.cmd clean test` — 296 tests, failures 0, errors 0, skipped 0
- Frontend: `npm run check` — TypeScript 통과, 22 files / 209 tests 통과, Production Build 통과
