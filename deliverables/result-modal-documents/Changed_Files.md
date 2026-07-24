# Phase 7 Rack Exhaustion Result Modal 변경 파일

## Patch ZIP 포함 파일

1. `frontend/src/types/game.ts`
   - `GameResultSnapshot` 타입 추가

2. `frontend/src/stores/game.ts`
   - RACK_EXHAUSTED 종료 전 승자 Snapshot 생성
   - Active Game Context와 결과 UI 상태 분리
   - 중복 이벤트 방어 유지
   - `acknowledgeTerminalResult()` 추가
   - Logout/새 게임 진입 시 결과 정리

3. `frontend/src/views/GameView.vue`
   - RACK_EXHAUSTED 즉시 Lobby 이동 중단
   - 결과 전용 배경 렌더링
   - 명시적 Lobby 이동 처리와 이중 클릭 잠금
   - PLAYER_LEFT/PLAYER_FORFEIT 기존 즉시 이동 유지

4. `frontend/src/components/game/GameResultModal.vue`
   - 승자/패자 결과 Modal
   - 승자 Nickname, Seat, 기존 Seat 색상, Rack 소진 사유 표시
   - X/Cancel/배경 클릭/ESC 닫기 미지원

5. `frontend/src/__tests__/GameStore.spec.ts`
   - Snapshot, 승자/패자, 중복 이벤트, Reply 순서, acknowledge/Logout 테스트

6. `frontend/src/__tests__/GameView.spec.ts`
   - Modal, 입력 차단, Route 유지, 명시적 Lobby 이동, 이중 클릭, 새로고침, Forfeit 회귀 테스트

## Backend

이번 작업 변경 없음.

## 금지 항목 준수

- Backend 종료 지연 없음
- Room CLOSED 정책 변경 없음
- Active Game DB 유지 없음
- Production Debug Endpoint 없음
- 결과 DB 컬럼 없음
- 브라우저 영구 저장소 사용 없음
- 기존 테스트 삭제 없음
- Docker/Kubernetes 배포 없음
