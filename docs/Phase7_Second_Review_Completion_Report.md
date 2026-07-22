# Phase 7 Second Review 완료 보고

## 결과

Phase 7의 append-only TurnDraft/Commit을 서버 Committed Table 기반의 단일 Local Working Table과 전체 Candidate Table Commit으로 교체했다. 첫 등록 전 baseline lock, 첫 등록 후 재조합, 실패 rollback, 권위 Snapshot 복구, Adaptive Rack, 내 턴 강조가 적용됐다.

## 필수 구현

- 전체 Candidate `tableMelds` WebSocket 계약
- TurnCommitValidator·InitialMeldValidator·TableRearrangementValidator 연결
- 기존 Meld 수정·분리·병합·순서 변경·Rack Tile 직접 추가
- baseline TABLE 타일 정확 보존과 requester Rack 기여 검증
- UNIQUE 제약을 유지한 GameMeld/Table Tile 원자적 reconciliation
- Cancel, turn end/timeout, stale, reconnect 복구
- 21~30장 Adaptive Rack과 녹색 내 턴 UX
- 첫 등록 완료/이번 제출 점수 상태 표시

## 선택 리팩터링

- `useWorkingTable`로 baseline/history/pending commit 책임을 한 composable에 집중했다.
- 기존 `useTurnDraft` export를 호환 shim으로 유지했다.
- `rackLayout.ts`를 순수 함수로 분리해 viewport 계산을 DOM 없이 테스트했다.

## 의도적으로 제외

- Joker 회수·재사용
- 게임 종료
- Timeout Scheduler 구현
- 신규 DB Migration
- 사용자 계정/Fixture/게임 데이터 변경

## 자동 검증

- Backend clean test: 296/296 통과
- Frontend check: TypeScript 통과, 209/209 통과, Production Build 통과
- Docker build/recreate: 성공, 3 containers healthy
- Flyway V5: validated/up-to-date
- Backend/Frontend Health: HTTP 200

## Runtime 판정

새 build는 실제 Compose에 반영됐고 기존 두 게임 세션의 REST 200/WebSocket 연결을 확인했다. 별도 브라우저 로그인 화면은 Console error 0이었다. 다만 인증된 Chrome 게임 화면의 직접 제어는 안전한 URL 확정 단계에서 중단됐으므로, 재조합/Commit/21~30장 viewport 수동 Runtime은 미실행으로 남긴다. 자세한 절차와 사실 기록은 `Phase7_Second_Review_Runtime_Guide.md`에 있다.

