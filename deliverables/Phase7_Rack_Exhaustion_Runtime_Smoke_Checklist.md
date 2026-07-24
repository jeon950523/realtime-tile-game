# Runtime Smoke Checklist

자동 검증 이후 FINAL 승격을 위한 최소 실환경 확인 절차다.

## 준비

- 실제 Backend + MySQL + Frontend 기동
- 서로 다른 사용자로 브라우저 2개 로그인
- 테스트 전용 DB Fixture 또는 Integration 환경에서 현재 턴 사용자의 Rack을 유효 마지막 Meld만 남은 상태로 준비
- 운영 Debug Endpoint는 사용하거나 추가하지 않음

## 실행

1. 현재 턴 사용자가 마지막 유효 Meld를 Commit한다.
2. 승자 브라우저에서 다음을 확인한다.
   - `모든 Rack 타일을 소진하여 승리했습니다.`
   - 게임 화면에서 로비로 이동
   - Active Game 복구 시 해당 게임이 다시 열리지 않음
3. 패자 브라우저에서 다음을 확인한다.
   - `상대 플레이어가 모든 Rack 타일을 소진하여 게임이 종료되었습니다.`
   - 게임 화면에서 로비로 이동
   - Active Game 조회 결과 없음
4. 서버/브로커 로그에서 다음을 확인한다.
   - `GAME_TERMINATED` 1회
   - `terminationReason=RACK_EXHAUSTED`
   - winner IDs 존재, exited IDs null
   - `ROOM_CLOSED`와 `ROOM_REMOVED` 전달
5. DB에서 다음을 확인한다.
   - Game FINISHED, winner 지정
   - 승자 WINNER, 나머지 LOSER
   - Room CLOSED
   - 모든 RoomPlayer leftAt 기록
   - turnNumber/currentTurnId 불변

## 승격 기준

모든 항목이 통과하면 검수 판정을 `CONDITIONAL PASS`에서 `PASS`로 올리고 Phase 7 기능을 FINAL로 닫는다.
