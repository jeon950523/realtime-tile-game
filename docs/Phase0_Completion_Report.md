# Phase 0 완료 보고서

작성일: 2026-07-14
상태: 조건부 통과 — 사용자 환경 REST·DB·최초 STOMP 확인, 수동 재연결 직접 검증과 Backend 테스트 재실행 필요

## 1. 작업 목적

Backend, Frontend, MySQL, REST, CORS, WebSocket/STOMP가 후속 Phase의 기능을 안전하게 수용할 수 있는 최소 개발 기반을 만든다.

## 2. 구현 내용

### Backend

- Java 17 기준 Spring Boot 3.5.15 프로젝트
- REST, Validation, JPA, Security, WebSocket/STOMP
- MySQL Connector와 Flyway
- 공통 성공 응답과 공통 오류 응답
- Spring Security 401/403 JSON 오류 계약
- 환경변수 기반 DB·CORS 설정
- `ddl-auto=validate`, `open-in-view=false`
- JPA Auditing
- `GET /api/health`
- `SELECT 1` DB 연결 점검
- `/ws` STOMP endpoint
- `/app/system.health.ping` → `/topic/system.health`
- SHA-512 검증이 포함된 Maven 실행 스크립트

### Frontend

- Vue 3 + TypeScript + Vite
- Pinia, Vue Router, Axios, STOMP Client
- REST/MySQL/WebSocket 상태 확인 화면
- API와 실시간 클라이언트 분리
- 최초 연결과 수동 재연결 lifecycle 분리
- deactivate 완료 후 activate하는 안전한 수동 재연결
- subscription 중복 방지와 연결 중 중복 클릭 차단
- 환경변수 기반 REST·WebSocket URL
- 최소 1024px 기준 레이아웃

### 실행 환경

- `compose.yaml` MySQL 8.4.10
- 루트 및 프론트 환경변수 예시
- 원본 기획 문서 16종 포함
- 직접 검증 가이드와 변경 파일 목록

## 3. 자동 테스트 결과

### Backend

```text
HealthApiIntegrationTest: 3개 통과
- REST/DB health 응답
- 인증 필요 endpoint의 401 공통 JSON 응답
- CORS 허용 origin

WebSocketHealthIntegrationTest: 1개 통과
- 실제 STOMP 연결·구독·publish·수신

총 4개 완료 / 실패 0
```

테스트 DB는 H2의 MySQL 호환 모드를 사용했다.

### Frontend

```text
App.spec.ts: 1개 통과
ConnectionStore.spec.ts: 5개 통과
SystemHealthClient.spec.ts: 5개 통과
총 11개 완료 / 실패 0
TypeScript type-check 통과
Vite production build 통과
npm audit 취약점 0건
```

최종 안정화 작업에서 `npm ci`와 `npm run check`를 실제 재실행했다.

## 4. 시니어 기준 검수에서 발견·수정한 사항

1. Vue 템플릿에서 `import.meta.env`를 직접 참조해 production build가 실패하던 문제를 발견했다. `<script setup>` 상수로 이동해 타입 검사와 빌드를 통과시켰다.
2. Windows `mvnw.cmd`의 괄호 블록 내부 변수 확장 문제를 발견했다. delayed expansion을 적용해 최초 Maven 다운로드 경로를 안정화했다.
3. 인증이 필요한 REST 요청이 403으로 반환되던 의미 오류를 발견했다. 미인증은 401, 권한 부족은 403으로 분리했다.
4. Spring Security 오류가 공통 JSON 계약을 우회하던 문제를 수정했다.
5. `application.yml`의 로컬 DB 계정 기본값을 제거해 실제 비밀정보가 환경변수 없이 사용되지 않게 했다.
6. 개발 의존성 감사에서 Vite와 Vitest 보안 권고를 발견했다. Vite 7.3.6, Vitest 3.2.7로 패치하고 전체 검증과 `npm audit` 취약점 0건을 확인했다.
7. 활성 STOMP client에 최초 연결 메서드를 다시 호출해 수동 재연결이 `CONNECTING`에 고정되던 문제를 발견했다. 최초 연결과 수동 재연결을 분리하고 기존 연결 종료 완료 후 재활성화하도록 수정했다.
8. `package-lock.json`의 내부 전용 registry URL을 제거하고 `registry.npmjs.org` 기준으로 정리했다.

## 5. 정합성·원자성 관점 검수

Phase 0에는 아직 GameState 변경이 없다. 대신 후속 구현을 위해 다음 경계를 고정했다.

- 도메인 규칙이 Controller·WebSocket Handler로 유출되지 않는 패키지 구조
- 환경과 통신 실패를 상태 변경과 분리
- REST 성공·실패 계약 분리
- 공개 WebSocket health topic만 제공
- JPA Entity 직접 응답 없음
- DB 스키마 자동 수정 금지

## 6. 알려진 한계

- 사용자 환경에서는 Docker Desktop, MySQL 8.4 healthy, Spring Boot, `/api/health`, Database UP, Vue/Vite, 최초 STOMP 연결까지 확인됐다.
- 이번 patch의 수동 재연결은 사용자 브라우저에서 3회 반복과 중복 연결 여부를 다시 확인해야 한다.
- 현재 작업 환경의 Backend 테스트 재실행은 `repo.maven.apache.org` DNS 해석 실패로 Maven 배포본 다운로드 전에 종료됐다. 이번 실행에서 Backend 테스트가 통과했다고 간주하지 않는다.
- 기존 Backend 자동 테스트의 DB 연결은 H2 MySQL 호환 모드로 검증했다.
- JWT는 Phase 2 범위이므로 현재 Security는 빈 사용자 저장소만 둔다.
- Flyway 기반은 준비했지만 Phase 0에는 생성할 도메인 테이블이 없어 migration SQL이 없다.
- WebSocket 인증과 사용자별 private destination 보안은 게임 세션 구현 전에 별도 적용해야 한다.

## 7. 기능적 리팩터링 판단

현재 Phase 0 범위에서 즉시 필요한 기능적 리팩터링은 모두 반영했다.

후속 필수 교체 지점:

- Phase 2: 빈 `UserDetailsService`를 JWT 인증 구조로 교체
- Phase 3 이후: 방·게임 참가 권한을 REST와 STOMP 양쪽에 적용
- Phase 4 이후: 공개 Snapshot과 개인 Snapshot을 별도 DTO로 유지

## 8. 효율성 리팩터링 판단

현재 요청 규모에서는 추가 캐시, 메시지 브로커, Redis, 복잡한 CQRS가 필요하지 않다.

후속 측정 후 검토할 항목:

- 서로 다른 게임 병렬 처리와 게임별 Lock 대기 시간
- WebSocket 이벤트 전송을 Lock 밖에서 처리하는 구조
- 실제 MySQL 환경의 Hikari pool 크기
- 프론트 route 단위 lazy loading

지금 적용하면 과도한 추상화가 되므로 Phase 0에는 도입하지 않았다.

## 9. 최종 판정

```text
정적 구조 검수: 통과
package-lock 내부 registry 제거: 통과
Frontend npm ci: 통과
Frontend 자동 테스트: 11/11 통과
Frontend TypeScript check: 통과
Frontend production build: 통과
npm audit: 취약점 0건
Backend 자동 테스트: 이번 재실행 미완료 — Maven 저장소 DNS 실패
/api/health: 사용자 환경 확인
Database UP: 사용자 환경 확인
최초 WebSocket/STOMP: 사용자 환경 확인
수동 재연결: 자동 테스트 통과 / 사용자 브라우저 직접 확인 필요
```

Phase 0 최종 안정화 patch는 조건부 통과다. 사용자 환경에서 Backend 4/4 재실행과 브라우저 수동 재연결 검증까지 통과한 뒤 Phase 0을 최종 완료로 닫는다.
