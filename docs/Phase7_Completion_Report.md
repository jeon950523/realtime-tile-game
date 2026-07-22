# Phase 7 Completion Report

## 결과

Phase 7 최소 게임 루프를 구현하고 실제 2계정 브라우저 시나리오까지 통과했다.

```text
현재 Player
-> Rack RUN Hold Group Drag
-> TurnDraft 2 Meld
-> 같은 턴 첫 등록 합계 30점
-> 서버 Rule Engine 검증과 COMMIT
-> Rack 감소 + Table 영속
-> initialMeldCompleted
-> gameVersion 증가
-> 공개/개인 상태 동기화
-> 다음 Player 턴
```

대표 Runtime은 RED 789=24와 BLUE 123=6을 제출했다.

## 필수 구현

- V5 `game_melds`와 TABLE tile-Meld 관계
- Typed `tableMelds`
- `TurnCommitValidator` 연결과 규칙 오류 매핑
- 비관적 잠금 기반 원자적 Commit Service
- actionId replay, gameVersion, 현재 턴, Rack 소유권 검증
- `/app/games/{gameId}/turn/commit`과 참가자 전용 STOMP 권한
- COMMIT 후 Meld 이벤트와 사용자별 Private Snapshot
- TurnDraft partition, 편집, Undo, Cancel, stale/reconnect 복구
- RUN/GROUP Hold 시각 그룹과 복수 타일 Overlay
- Phase 6 fixed slot/RAF/dead zone/teleport motion 유지
- 확정 Table 렌더링과 새로고침 복원

## 선택 리팩터링

- `GameTurnStateFactory`로 Rule Engine 입력 어댑터를 분리했다.
- `RuleViolationMapper`로 도메인 오류와 전송 오류의 경계를 분리했다.
- 공개 이벤트가 개인 Rack보다 먼저 도착하는 순서 경합을 sync revision으로 흡수했다.
- 재현 가능한 local-only Exact-30 SQL 픽스처를 추가했다.

범위 밖인 기존 Table 재조합, Joker 회수·재사용, 게임 종료, Timeout Scheduler는 구현하지 않았다.

## 자동 테스트

```text
Backend: .\mvnw.cmd clean test
Tests run: 290, Failures: 0, Errors: 0, Skipped: 0

Frontend: npm run check
Type-check PASS
21 test files PASS
167 tests PASS
Vite production build PASS
```

## Runtime 검증

- MySQL 8.4.10, Backend, Frontend Compose 서비스 healthy
- Flyway V1~V5 validation/migration 성공
- Hold Drag로 RED 789와 BLUE 123을 서로 다른 Draft Meld에 배치
- 첫 등록 `30/30`, Commit 버튼 활성화
- COMMIT 성공 후 B Rack 14→8
- `game_melds`: RUN 24점, RUN 6점
- B `initial_meld_completed=1`
- `gameVersion=1`, `turnNumber=2`, 다음 Player A
- 새로고침 후 Table 유지
- A 계정 재로그인 후 같은 공개 Table, A Rack 12, 상대 B Rack count 8 확인
- 최종 브라우저 console error/warning 0

실제 Runtime 중 최초 COMMIT이 `FORBIDDEN`으로 연결 종료되는 문제를 발견했다. 원인은 STOMP 허용 정규식이 `draw|pass`만 포함한 것이었고, `commit`을 참가자 전용 경로에 추가한 뒤 통합 테스트와 동일 브라우저 시나리오를 재실행해 통과했다.

Flyway는 MySQL 8.4가 현재 라이브러리의 공식 검증 상한 8.1보다 새 버전이라는 경고를 출력했다. 실제 V5 적용과 Hibernate 기동은 성공했다.

## 산출물

```text
Phase7_Minimum_Turn_Draft_Initial_Meld_Commit_And_Next_Turn_Patch.zip
Phase7_Minimum_Turn_Draft_Initial_Meld_Commit_And_Next_Turn_Documents.zip
```

Patch ZIP은 이번 Phase에서 수정·생성한 파일 70개만 포함한다. Documents ZIP은 지시된 갱신 문서 8개와 신규 문서 7개, 총 15개만 포함한다.
