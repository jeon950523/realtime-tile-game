# 변경 파일 목록

## Patch ZIP 포함 파일

1. `backend/src/test/java/com/realtimetilegame/game/GameTurnCommitIntegrationTest.java`
   - Rack 잔여 정상 진행 계약 명시
   - Rack 0 종료 이벤트와 Room 이벤트 계약 보강
   - 종료 후 Draw/Pass/Commit 거부 검증
   - 무효 마지막 Meld 전체 롤백 검증
   - 같은 gameVersion 동시 마지막 Commit 단일 종료 검증

2. `frontend/src/__tests__/GameStore.spec.ts`
   - 내 승리/상대 승리 안내 문구를 정확 문자열로 검증

3. `frontend/src/__tests__/GameView.spec.ts`
   - GAME_TERMINATED 후 Active Game Context 제거, terminalRevision, Lobby 연결 및 `/lobby` 이동 검증

## 운영 코드

변경 없음.

## 제외 항목

- Production Debug Endpoint: 추가하지 않음
- 운영 규칙 완화: 없음
- 기존 테스트 삭제: 없음
- 기존 기대값 임의 완화: 없음
