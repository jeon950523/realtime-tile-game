# Phase 0 Document Consistency Cleanup 완료 보고서

작성일: 2026-07-14  
작업명: Phase 0 Document Consistency Cleanup

> 이 문서는 이전 `Phase 0 Document Consistency Cleanup`의 문서 전용 작업 기록이다.  
> 이후 Frontend 재연결 코드와 lockfile을 수정한 최종 안정화 결과는 `Phase0_Final_Stabilization_Report.md`를 기준으로 한다.

## 1. 작업 목적

Phase 0 최신 전체본의 소스 구조는 보존하고, 기준 문서 사이에 남아 있던 참가 인원·후반 기능 범위·문서 번호·JWT 범위·README 문서 트리 충돌만 작은 diff로 정리한다.

## 2. 수정 파일과 변경 이유

- `README.md`: 문서 트리에 `Phase0_Changed_Files.md`와 본 정합성 완료 보고서를 반영했다.
- `docs/Phase0_Changed_Files.md`: 추상 설명을 실제 상대 경로 전체 목록으로 교체했다.
- `docs/Phase0_Document_Baseline_Summary.md`: 해결 전 충돌 설명을 수정된 기준 문서와 일치하도록 갱신했다.
- `docs/specs/Realtime_Tile_Game_Rules_Spec_v1.md`: 참가 인원을 `2~4명`으로 통일했다.
- `docs/specs/Realtime_Tile_Game_Project_Planning_v1.md`: 랭킹·상대 전적·SPEED를 초기 CLASSIC 핵심 MVP 제외이자 Phase 11~13 구현 대상으로 명시했다.
- `docs/specs/Realtime_Tile_Game_SRS_v1.md`: Planning과 동일한 단계 범위로 정리했다.
- `docs/specs/Realtime_Tile_Game_Document_Index_v1.md`: 상위 장과 문서 항목 번호를 순차화했다.
- `docs/specs/Realtime_Tile_Game_New_Project_Start_Prompt_v1.md`: Phase 0 JWT 범위를 보안 경계와 확장 위치로 한정하고 실제 JWT는 Phase 2라고 명시했다.
- `docs/Phase0_Document_Consistency_Cleanup_Report.md`: 이번 정합성 작업의 범위와 검증 결과를 기록했다.

## 3. 문서 충돌 해결 결과

```text
Rules 참가 인원: 2~4인
랭킹·상대 전적·SPEED: 초기 CLASSIC 핵심 MVP 제외
후반 구현: Roadmap Phase 11~13
Document Index: 순차 번호 정상화
Phase 0 JWT: Security 경계·401/403 계약·확장 위치만 포함
실제 JWT 발급·필터·Refresh Token: Phase 2
```

## 4. 소스 코드 변경 여부

```text
Backend 소스 변경: 없음
Frontend 소스 변경: 없음
설정·의존성 변경: 없음
```

문서 수정 전후의 `backend/**`, `frontend/**` SHA-256을 비교해 동일함을 확인한다.

## 5. 자동 테스트 재실행

```text
Backend 자동 테스트 재실행
- 명령: ./mvnw test
- 결과: 미실행
- 사유: 현재 실행 환경에서 repo.maven.apache.org DNS 해석 실패
- 기존 완료 보고서 기록: 4/4 통과 유지
- 이번 작업에서 Backend 소스 변경: 없음

Frontend 자동 테스트 재실행
- 명령: npm ci --offline && npm run check
- Vitest: 3/3 통과
- TypeScript type-check: 통과
- Vite production build: 통과
- npm audit: 취약점 0건
```

Backend 4/4는 Phase 0 최초 자동 검수 기록이며 이번 환경에서 새로 실행한 결과로 표기하지 않는다.

문서만 변경했으며 자동 테스트 수치를 임의로 바꾸지 않는다.

## 6. Phase 0 최종 직접 검증 절차

1. 루트 `.env.example`을 `.env`로 복사하고 로컬 MySQL 비밀번호를 설정한다.
2. `docker compose up -d`로 MySQL 8.4.10을 실행한다.
3. `docker compose ps`와 MySQL health 상태를 확인한다.
4. 동일한 DB 환경변수로 `backend`에서 `./mvnw spring-boot:run` 또는 `mvnw.cmd spring-boot:run`을 실행한다.
5. `GET /api/health` 응답에서 애플리케이션과 DB 상태가 정상인지 확인한다.
6. 인증이 필요한 임의 endpoint에서 401 공통 JSON 계약을 확인한다.
7. 허용된 프론트 origin에서 CORS 응답을 확인한다.
8. `frontend/.env.example`을 `.env.local`로 복사하고 `npm ci`, `npm run dev`를 실행한다.
9. 브라우저 연결 확인 화면에서 REST, MySQL, WebSocket/STOMP 상태를 확인한다.
10. `/app/system.health.ping` 발행 후 `/topic/system.health` 수신을 확인한다.
11. 브라우저 콘솔과 Backend 로그에 Error가 없는지 확인한다.
12. 검증 완료 후 `docker compose down`으로 환경을 종료한다.

실제 MySQL과 브라우저 검증이 끝나기 전까지 기존 `Phase0_Completion_Report.md` 상태는 `조건부 통과`로 유지한다.

## 7. 최종 판정

```text
문서 정합성 수정: 완료
Rules 인원 기준: 2~4인 통일
랭킹 범위 표현: 초기 MVP 제외 / 후반 Phase 구현
Document Index 번호: 정상
Phase 0 JWT 범위: 정리 완료
README 문서 목록: 실제 구성과 일치
소스 코드 변경: 없음
최종 판정: 문서 보완 통과
```
