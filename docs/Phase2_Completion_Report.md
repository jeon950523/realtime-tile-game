# Phase 2 최종 완료 보고서

최신 기준: `phase0715-02-41-phase1-final.zip`  
작업: `Phase 2 — Authentication And User Profile`  
작성 기준: 2026-07-15

초기 Phase 2 자동 검증에 최종 보완 테스트가 추가되어 현재 기준은 Backend 156개, Frontend 34개다.

## 1. 최종 구현 범위

### Backend

- `users`, `refresh_tokens` Flyway V1 Migration
- User·RefreshToken Entity와 Repository
- 회원가입·로그인·재발급·로그아웃
- BCrypt 비밀번호 저장
- Spring Security JWT Resource Server
- HS256 Access Token 발급·검증
- Refresh Token 256bit opaque 생성과 SHA-256 hash 저장
- Refresh Token pessimistic lock 회전과 재사용 차단
- HttpOnly·SameSite Strict Cookie
- 만료·잘못된 Refresh Cookie 실패 응답에서 Cookie 삭제
- 보호 REST 요청의 현재 DB 사용자 상태 공통 검사
- GET `/api/me`
- PATCH `/api/me/profile`
- 공통 인증·비즈니스 오류 JSON

### Frontend

- Pinia `authStore`
- Access Token 메모리 저장
- Axios Authorization Header
- 동시 401 Single Flight 재발급
- 원 요청 최대 1회 재시도
- 재발급 실패 시 익명 상태 전환
- 앱 시작 세션 복구
- 로그아웃 성공 시에만 인증 상태 제거와 `/login` 이동
- 로그아웃 실패 시 인증 상태·현재 화면 유지와 안전한 오류 표시
- 로그아웃 중복 요청 차단
- `/login`, `/register`, `/profile`, `/health`
- 인증·guestOnly Route Guard
- 회원가입·로그인·프로필 화면

## 2. Backend 결과

검수 실행:

```text
Maven Wrapper clean test
검수 환경 전용 임시 Maven settings 사용
프로젝트 설정과 Patch에는 포함하지 않음
```

결과:

```text
Tests run: 156
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

회귀 구성:

```text
Phase 1 순수 규칙 엔진: 108개
Health REST: 3개
Health WebSocket/STOMP: 1개
Phase 2 인증·프로필 및 보안: 44개
전체: 156개
```

검수 JVM은 Java 21.0.10이었고 Maven Compiler는 Java 17 `release` 대상으로 컴파일했다. 사용자 Java 17 JVM에서 `./mvnw.cmd clean test` 단일 명령 재검증이 필요하다.

## 3. Frontend 결과

실행 명령:

```text
npm ci
npm run check
```

결과:

```text
npm ci: 성공
Vitest: 34/34 통과
TypeScript: 통과
Production Build: 통과
```

Access Token 영속 저장 금지 정적 검색과 `frontend/package-lock.json` 무변경을 확인했다. 실제 브라우저 Cookie·리다이렉트 동작은 사용자 환경 검증이 필요하다.

## 4. DB / Migration

- V1 Migration: H2 MySQL mode 적용 통과
- Hibernate `ddl-auto=validate`: 통과
- Entity–Migration `DATETIME(6)`, `CHAR(64)` 정합성: 통과
- Refresh Token 원문 미저장 테스트: 통과
- 실제 MySQL 8.4 적용: 사용자 환경에서 확인 필요

## 5. Authentication

| 항목 | 최종 결과 |
|---|---|
| 회원가입 | 구현·통합 테스트 통과 |
| 이메일·닉네임 정규화와 중복 차단 | 통과 |
| BCrypt 비밀번호 저장 | 통과 |
| 로그인 | 구현·통합 테스트 통과 |
| BLOCKED 로그인 차단 | 통과 |
| DELETED 로그인 차단 | 통과 |
| Access Token | 발급·서명·issuer·audience·subject·role 검증 통과 |
| Refresh Cookie | HttpOnly·SameSite Strict·Path 적용 테스트 통과 |
| Refresh hash 저장 | SHA-256 hash만 저장 통과 |
| Rotation | 기존 폐기·새 Cookie 발급 통과 |
| 이전 Token 재사용 | 차단 통과 |
| 동시 재발급 | 하나만 성공 통과 |
| 새로고침 복구 | Frontend 자동 테스트 통과, Browser 확인 필요 |

## 6. Active User Boundary

보호 REST 요청은 JWT 인증 이후 `ActiveUserAuthorizationFilter`에서 현재 DB 사용자를 조회한다.

```text
ACTIVE: 요청 진행
BLOCKED: 403 USER_BLOCKED
DELETED: 401 USER_DELETED
사용자 행 없음: 401 AUTHENTICATION_REQUIRED
```

기존 Access Token 발급 이후 사용자가 BLOCKED 또는 DELETED로 변경돼도 GET·PATCH 보호 API에서 즉시 차단된다. JWT status Claim은 사용하지 않으며 Controller별 상태 검사 복사도 없다.

## 7. JWT 필수 Claim

필수 계약:

```text
sub, iss, aud, iat, exp, jti, role
```

검증 결과:

- `iat` 누락 차단
- `exp` 누락 차단
- `jti` 누락·blank 차단
- `exp <= iat` 차단
- 허용 Clock Skew를 초과한 미래 `iat` 차단
- JWT 문자열 직접 파서 없음

`expirationBeforeIssuedAtIsRejected`는 `iat`와 `exp`가 모두 현재보다 미래이면서 허용 Clock Skew 이내이고 `exp < iat`인 Token을 사용한다. 일반 만료 검사가 아니라 시간 관계 Validator를 직접 증명한다.

## 8. Refresh Token / Cookie

- 256bit opaque Token
- 원문 DB·JSON·로그 저장 없음
- SHA-256 hash와 UNIQUE 제약
- pessimistic write lock 회전
- 이전 Token 재사용 차단
- 로그아웃 시 폐기와 Cookie 삭제
- `REFRESH_TOKEN_INVALID`·`REFRESH_TOKEN_EXPIRED` 실패 응답에서도 Cookie 삭제

## 9. Logout Reliability

최종 정책:

```text
로그아웃 성공
→ 서버 204
→ 인증 상태 제거
→ /login 이동

로그아웃 실패
→ 인증 상태 유지
→ 현재 화면 유지
→ 안전한 오류 메시지
→ 재시도 가능
```

빠른 연속 클릭은 동일 Promise를 공유해 요청을 한 번만 보낸다.

## 10. Profile

| 항목 | 결과 |
|---|---|
| 내 프로필 조회 | 통과 |
| Phase 2 전적·Active Session 기본값 | 통과 |
| 닉네임 수정 | 통과 |
| 아바타 수정 | 통과 |
| 자신의 닉네임 유지 | 통과 |
| 중복 닉네임 차단 | 통과 |
| 잘못된 아바타 원자성 | 통과 |
| BLOCKED/DELETED 기존 Token 수정 차단 | 통과 |

## 11. Regression

```text
Phase 1 규칙 엔진 변경: 없음
Health REST: 통과
Health WebSocket/STOMP: 통과
WebSocket endpoint 변경: 없음
WebSocket 사용자 인증 구현: 없음
DB Migration 최종 보완 변경: 없음
REST 계약 변경: 없음
방·로비·GameState 구현: 없음
frontend/package-lock.json 변경: 없음
Frontend Dependency 변경: 없음
```

## 12. 실제 미실행 항목

다음은 자동 검증으로 대체하지 않았으며 사용자 환경에서 확인해야 한다.

- Java 17 JVM의 단일 `mvnw.cmd clean test`
- 실제 MySQL 8.4 V1 Migration과 `ddl-auto=validate`
- 기존 Access Token 사용자 상태를 DB에서 BLOCKED/DELETED로 바꾼 브라우저·API 검증
- HttpOnly·SameSite·Path·Secure Cookie 실동작
- 새로고침 `reissue → /api/me` 복구
- 서버 로그아웃 실패 시 상태·화면 유지
- 정상 로그아웃 204와 Cookie 삭제
- 만료·잘못된 Refresh Cookie 실패 삭제
- 기존 Health REST·STOMP 실동작

## 13. 알려진 한계

- 실제 MySQL과 브라우저 Cookie 동작은 사용자 환경 확인 필요
- 운영 HTTPS의 `Secure=true` Cookie는 배포 환경에서 확인 필요
- 다중 기기 세션 관리와 Token Family 탈취 탐지는 제외
- WebSocket 인증과 Principal은 후속 Phase
- 전적과 Active Session은 계약에 맞춘 0/null 기본값만 반환

## 14. 최종 판정

```text
정적·자동 검증 기준: 통과
사용자 Java 17·MySQL 8.4·브라우저 직접 검증: 필요
Phase 2 최종 완료 확정: 사용자 직접 검증 후
Phase 3: 사용자 직접 검증 전 보류
```
