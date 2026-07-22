# Phase 7 Third Review Two-Browser Runtime Guide

## 사전 조건

1. 현재 작업 트리로 Backend/Frontend를 실행한다.
2. 일반 Chrome과 새 시크릿 Chrome을 준비한다.
3. 서로 다른 기존 테스트 계정으로 로그인한다.
4. DevTools의 Console과 Network를 두 창 모두 연다.
5. 자동 fixture나 운영 사용자 데이터는 사용하지 않는다.

## 1. 신규 세션 구독 준비

1. 시크릿 창을 완전히 닫았다가 새로 연다.
2. 로그인 후 진행 중 게임으로 이동한다.
3. 최초 Private State와 game subscription이 준비되기 전 Draw/PASS/Commit이 disabled인지 확인한다.
4. 준비된 뒤 첫 행동을 한 번만 클릭한다.

기대 결과:

- `/topic/games/{gameId}`, `/user/queue/game-state`, `/user/queue/replies`가 한 세트만 활성화된다.
- 행동 publish는 정확히 한 번 발생한다.
- Reply 또는 Private State 수신 뒤 `처리 중`이 해제된다.

## 2. 교차 플레이어 기존 Meld 연장

1. 일반 창 Player A가 RED 9-10-11을 Commit한다.
2. 다음 턴의 시크릿 창 Player B가 자기 Rack의 RED 12를 기존 Meld 끝에 Drag한다.
3. B가 턴 확정을 누른다.

기대 결과:

- RED 9-10-11-12, RUN, 42점으로 Commit된다.
- B Rack만 1장 감소한다.
- 양쪽 Table과 gameVersion이 같고 다음 Player로 턴이 이동한다.
- B 화면이 영구 `처리 중`에 머물지 않는다.

## 3. Reply/State 유실 복구

개발 환경에서 DevTools offline, Backend 일시 중지 또는 WebSocket disconnect 중 한 방법을 사용한다. 상태 확인 없이 같은 버튼을 반복 클릭하지 않는다.

1. Candidate를 만든 뒤 Commit한다.
2. Reply/Private State 중 하나 또는 연결을 끊는다.
3. 9초까지 기다린다.

기대 결과:

- REST `/api/games/{gameId}` 조회가 실행된다.
- 서버에 반영된 Candidate면 Working Table이 COMMITTED로 정리된다.
- 미반영이면 Candidate가 남고 새 actionId로 재시도할 수 있다.
- 자동 재전송이나 중복 Commit은 없다.
- REST도 실패해도 `처리 중`은 해제되고 Candidate는 권위 반영으로 오인되지 않는다.

## 4. 버튼 없는 Drag & Drop

다음 폭에서 반복한다: 2560×1440, 1920×1080, 약 1440px, 작은 노트북 폭.

- 같은 Meld 안 순서 변경
- 다른 Meld로 타일 이동
- 빈 작업판 drop으로 새 Meld
- Rack 타일을 기존 Meld의 특정 위치에 삽입
- Rack-origin 타일을 Rack으로 다시 drop
- TABLE-origin 타일을 Rack으로 drop했을 때 변경 없음
- source Meld의 마지막 타일 이동 후 자동 제거
- Undo와 Cancel

기대 결과:

- 타일 아래 작은 버튼과 `다음 Meld와 병합`이 없다.
- drag source는 흐려지고 삽입 위치에는 세로 marker가 표시된다.
- 잠긴 Meld는 drop을 받아들이지 않고 이유가 유지된다.
- Rack-only 새 Meld에만 `전체 Rack`이 표시된다.

## 5. 증거 기록

각 창에서 다음을 기록한다.

- Console error 유무
- Commit actionId와 Reply actionId
- 전/후 gameVersion
- 전/후 양 Player Rack count
- 최종 tableMelds 순서와 tile IDs
- 영구 Pending 여부

