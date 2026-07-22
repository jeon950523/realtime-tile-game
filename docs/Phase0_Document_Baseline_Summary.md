# Phase 0 문서 기준 확인 및 핵심 요약

작성일: 2026-07-14

## 1. 킥오프 ZIP 내부 문서 목록

1. `Realtime_Tile_Game_Project_Development_Guidelines_v2.md`
2. `Realtime_Tile_Game_Document_Index_v1.md`
3. `Realtime_Tile_Game_New_Project_Start_Prompt_v1.md`
4. `Realtime_Tile_Game_Project_Planning_v1.md`
5. `Realtime_Tile_Game_Rules_Spec_v1.md`
6. `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
7. `Realtime_Tile_Game_SRS_v1.md`
8. `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
9. `Realtime_Tile_Game_ERD_Table_Spec_v1.md`
10. `Realtime_Tile_Game_REST_API_Spec_v1.md`
11. `Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`
12. `Realtime_Tile_Game_Server_GameState_Model_v1.md`
13. `Realtime_Tile_Game_Rule_Engine_Class_Design_v1.md`
14. `Realtime_Tile_Game_Test_Case_Matrix_v1.md`
15. `Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md`
16. `Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md`

기준 문서 16개는 `docs/specs`에 포함했다. 이후 확인된 문서 내부 충돌은 기획 의도를 유지한 작은 diff로 정리했다.

## 2. Project Development Guidelines v2 핵심 기준

- 단순 동작보다 책임, 의도, 테스트 가능성, 변경 안전성이 드러나는 코드를 작성한다.
- 게임 상태 정합성과 무결성을 기능 수보다 우선한다.
- 상태 변경은 현재 상태를 직접 조금씩 수정하지 않고 Candidate를 만든 뒤 전체 검증 후 한 번에 Commit한다.
- 클라이언트는 의도만 전달하며 현재 턴, 소유권, 규칙, 시간, 점수의 최종 권한은 서버가 가진다.
- 공개 Snapshot과 사용자별 Private Snapshot을 분리한다.
- 동시성과 재전송을 기본 전제로 두고 게임별 Lock, `actionId`, `gameVersion`, DB UNIQUE 제약을 사용한다.
- CLASSIC/SPEED 차이는 정책 객체 경계로 분리하되 현재 필요 이상의 추상화는 금지한다.
- REST·WebSocket·DB 계약 변경은 일반 리팩터링으로 취급하지 않는다.
- 비밀정보는 환경변수로 관리하고, DB 변경은 Flyway 마이그레이션으로 남긴다.
- 수정은 작은 diff 단위로 수행하며 신규·실패·회귀 테스트를 완료 조건에 포함한다.

## 3. Implementation Roadmap 핵심 기준

Phase 0의 목적은 게임 기능 구현이 아니라 Backend, Frontend, DB, WebSocket이 서로 연결될 수 있는 기반을 만드는 것이다.

필수 범위:

- Backend: Java 17, Spring Boot, MySQL, JPA, Validation, Security, WebSocket/STOMP, 공통 응답, 공통 예외, Auditing
- Frontend: Vue 3, Vite, Pinia, Vue Router, Axios, STOMP Client, 환경변수, 기본 레이아웃
- 연결 확인: `/api/health`, DB 연결, WebSocket/STOMP, CORS
- 산출물: 실행 가이드, 환경변수 예시, README, 자동 테스트

Phase 0에서 금지한 선행 구현:

- RUN/GROUP 등 규칙 엔진
- JWT 회원가입·로그인
- 방·로비
- GameState와 턴 처리
- 랭킹·상대 전적·SPEED

## 4. Rules Spec 핵심 기준

후속 구현에서 보존해야 할 규칙 경계다.

- 참가자는 2~4명이며 총 106개 고유 `tileId`를 사용한다.
- 모든 확정 판정은 서버가 수행한다.
- CLASSIC은 첫 등록 30점, 기본 턴 120초, 손패 0개 승리다.
- SPEED는 전체 5분, 기본 턴 20초, 첫 등록 없음, 점수 승부이며 랭킹에 반영하지 않는다.
- RUN, GROUP, 조커, 기존 테이블 재조합을 지원한다.
- 일반 턴 확정에는 손패 타일 최소 1개 기여가 필요하다.
- 검증 실패와 시간 초과는 확정 상태를 부분 변경하지 않는다.
- 시간 초과 시 TurnSnapshot을 복원한 뒤 자동 드로우 또는 PASS와 다음 턴 전환을 원자적으로 처리한다.
- 최종 동점은 `DRAW`다.

Phase 0은 이 규칙을 구현하지 않고, 규칙 도메인이 통신·JPA와 분리되어 들어갈 패키지 경계만 보존한다.

## 5. SRS 핵심 기준

- REST는 인증·초기 조회·복원 조회 등에 사용하고, WebSocket은 실시간 상태 변경과 이벤트에 사용한다.
- 실시간 GameState는 우선 서버 메모리, 사용자·방·결과·행동 로그는 MySQL에 저장한다.
- 실패한 요청은 확정 GameState를 변경하지 않아야 한다.
- 상대 손패 원문은 REST, WebSocket, 로그, 프론트 상태 어디에도 노출하면 안 된다.
- 일반 REST 목표는 1초 이내, WebSocket 이벤트 목표는 500ms 이내다.
- 한 게임의 상태 변경은 직렬화하고 서로 다른 게임은 병렬 처리할 수 있어야 한다.
- 성공한 상태 변경마다 `gameVersion`이 단조 증가해야 한다.

핵심 불변조건:

```text
106개 tileId는 정확히 한 위치에 존재한다.
같은 tileId는 중복되지 않는다.
tileId는 유실되지 않는다.
확정 테이블은 모두 유효하다.
현재 턴 사용자만 상태를 변경한다.
실패 요청은 확정 상태를 변경하지 않는다.
상대 손패 원문은 전송하지 않는다.
```

## 6. Phase 0 구현 판단

킥오프 ZIP에는 소스 코드가 없고 문서만 존재했다. 따라서 빈 워크스페이스 기준으로 Phase 0 기반을 생성했다.

범위 판단:

```text
구현: 실행 기반, 연결 경계, 공통 계약, 자동 테스트
보류: 실제 게임 도메인과 사용자 기능
```

## 7. 문서 간 정합성 확인

`Phase 0 Document Consistency Cleanup`에서 다음 충돌을 원본 기준 문서에 직접 정리했다.

1. `Rules Spec`의 1차 MVP 요약에 남아 있던 참가 인원 `3~4명`을 현재 확정 기준인 `2~4명`으로 수정했다.
2. Planning과 SRS의 제외 범위를 `초기 CLASSIC 핵심 MVP 제외`와 `전체 프로젝트 제외`로 분리했다. 랭킹·상대 전적·SPEED는 전체 프로젝트 제외가 아니며 각각 Roadmap Phase 11~13에서 구현한다.
3. `Document Index`의 상위 장 번호와 문서 항목 번호를 처음부터 끝까지 순차화했다.
4. 새 프로젝트 시작 프롬프트의 Phase 0 보안 범위를 `Spring Security 기본 경계`, `401/403 공통 오류 계약`, `JWT 적용 확장 위치`로 명시했다. 실제 JWT 발급·필터·Refresh Token은 Phase 2에서 구현한다.
5. 간략한 초기 Phase 구분과 상세 Roadmap의 Phase 0~15가 함께 존재할 때 실제 개발 순서는 `Implementation Roadmap`을 우선한다.
6. 충돌 시 적용 우선순위는 `Project Development Guidelines v2 → 최신 확정 요구사항 → Implementation Roadmap → 세부 명세`다. 규칙 자체가 새로 충돌하면 코드를 작성하기 전에 문서를 먼저 갱신한다.

## 8. Phase 0 최종 안정화 반영

`Phase 0 Final Stabilization And Documentation Consistency Cleanup`에서 다음을 추가 확인했다.

- Backend WebSocket endpoint와 REST 계약은 변경하지 않았다.
- Frontend 최초 연결과 수동 재연결 lifecycle을 분리했다.
- `package-lock.json`의 내부 전용 registry URL을 제거했다.
- 직접 검증 가이드에 Docker `.env`와 Spring Boot `DB_*` 환경변수의 차이를 명시했다.
- 문서 정합성 변경은 원본 기준 문서에 반영된 상태를 유지한다.
- Phase 1은 사용자 환경에서 Backend 테스트와 브라우저 수동 재연결 검증을 끝낸 뒤 시작한다.
