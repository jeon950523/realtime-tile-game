# Phase 7 Rack Exhaustion Result Modal Runtime Smoke Checklist

## 사전 조건

- 최신 소스로 Backend, MySQL, Frontend를 사용자가 직접 재배포
- 브라우저 A/B에서 서로 다른 사용자로 같은 게임 참여
- Production Debug Endpoint 없이 정상 게임 또는 테스트 환경 Fixture 사용

## 확인 절차

1. 브라우저 A가 마지막 유효 Meld를 Commit하여 Rack을 0으로 만든다.
2. 서버에서 Game이 즉시 `FINISHED`인지 확인한다.
3. Room이 즉시 `CLOSED`인지 확인한다.
4. 양 사용자 Active Game 조회가 모두 없음인지 확인한다.
5. 브라우저 A에 `승리했습니다!`와 A의 Nickname/Seat가 표시되는지 확인한다.
6. 브라우저 B에 `게임 종료`와 A의 Nickname/Seat가 표시되는지 확인한다.
7. 두 브라우저 모두 자동으로 Lobby 이동하지 않는지 확인한다.
8. Modal 배경 클릭으로 닫히지 않는지 확인한다.
9. ESC로 닫히지 않는지 확인한다.
10. X 및 일반 Cancel 버튼이 없는지 확인한다.
11. Tile Drag, Draw, Pass, Commit, Undo, Cancel, Rack 정렬, 게임 포기가 불가능한지 확인한다.
12. `게임 상태를 복구하는 중입니다.` 문구가 표시되지 않는지 확인한다.
13. A에서 `로비로 나가기`를 누르면 A만 `/lobby`로 이동하는지 확인한다.
14. B는 버튼을 누르기 전까지 결과 Modal을 계속 유지하는지 확인한다.
15. B에서 `로비로 나가기`를 누르면 `/lobby`로 이동하는지 확인한다.
16. 종료된 Room이 Lobby 목록에 없는지 확인한다.
17. 양 사용자 모두 새 방 생성 또는 입장이 가능한지 확인한다.
18. 다시 Rack 소진 결과 Modal을 띄우고 새로고침한다.
19. 서버 Active Game이 없으므로 `/lobby`로 이동하고 Modal이 복구되지 않는지 확인한다.
20. PLAYER_LEFT와 PLAYER_FORFEIT가 기존처럼 즉시 Lobby로 이동하는지 확인한다.

## Docker/Kubernetes

이번 작업에서는 Docker Build, kind load, kubectl rollout을 실행하지 않았다. 사용자가 기존 배포 절차에 따라 최신 이미지를 반영한 뒤 위 시나리오를 수행한다.

## 판정

- 전 항목 통과: `PASS / FINAL`
- 자동 테스트만 통과하고 Runtime 미실행: `CONDITIONAL PASS`
- 하나라도 기능 불일치: `FAIL`
