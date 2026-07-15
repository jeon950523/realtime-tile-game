# Phase 2 변경 파일 목록

작성 기준: 2026-07-15  
기준 전체본: `phase0715-02-41-phase1-final.zip`  
작업: `Phase 2 — Authentication And User Profile`

## 최종 추적 기준

```text
초기 Phase 2 Patch: 75개
최종 보안 안정화 Patch: 21개
최종 증거·문서 정합성 Patch: 7개
Backend 최종 테스트: 156개
Frontend 최종 Vitest: 34개
```

각 Patch는 수정·생성 파일만 포함한다. Phase 1 규칙 엔진, WebSocket endpoint, DB Migration의 최종 보완 변경, `frontend/package-lock.json`은 이번 최종 정합성 Patch에서 변경하지 않았다.

## 초기 Phase 2 Patch — 75개

## Root

| 경로 | 변경 | 이유 |
|---|---|---|
| `.env.example` | 수정 | JWT issuer·audience·TTL·Secret placeholder와 Refresh Cookie 설정 예시 추가 |
| `README.md` | 수정 | 현재 구현 범위를 Phase 2 인증·프로필과 최신 테스트 기준으로 갱신 |

## Backend — Build·Configuration·Migration

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/pom.xml` | 수정 | Spring Security 공식 JWT Resource Server 사용을 위한 starter 추가 |
| `backend/src/main/resources/application.yml` | 수정 | JWT·Refresh Token·Cookie 환경 설정과 UTC JDBC 시간 설정 추가 |
| `backend/src/test/resources/application-test.yml` | 수정 | H2 Flyway·ddl validate 및 테스트 전용 JWT 설정 추가 |
| `backend/src/main/resources/db/migration/V1__create_users_and_refresh_tokens.sql` | 생성 | `users`, `refresh_tokens`, UNIQUE·FK·INDEX·CHECK 제약 생성 |

## Backend — Common Error

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/common/error/BusinessException.java` | 생성 | 예상 가능한 인증·프로필 실패를 공통 오류 코드로 전달 |
| `backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java` | 수정 | 회원가입·로그인·Refresh Token·프로필 오류 코드와 HTTP 상태 추가 |
| `backend/src/main/java/com/realtimetilegame/common/error/GlobalExceptionHandler.java` | 수정 | `BusinessException`을 공통 JSON 계약으로 변환 |

## Backend — Security

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/config/SecurityConfiguration.java` | 수정 | 임시 메모리 인증 제거, JWT Resource Server·공개/보호 경로·BCrypt 설정 |
| `backend/src/main/java/com/realtimetilegame/security/AccessToken.java` | 생성 | Access Token 값과 만료 초를 명시적으로 전달 |
| `backend/src/main/java/com/realtimetilegame/security/CurrentUser.java` | 생성 | 검증된 JWT subject에서 현재 사용자 ID 추출 |
| `backend/src/main/java/com/realtimetilegame/security/JwtConfiguration.java` | 생성 | Base64 Secret 검증, HS256 Encoder·Decoder, issuer·audience·subject·role 및 필수 시간 Claim Validator 구성 |
| `backend/src/main/java/com/realtimetilegame/security/JwtProperties.java` | 생성 | Access Token 환경 설정 바인딩과 시작 시 유효성 검사 |
| `backend/src/main/java/com/realtimetilegame/security/JwtTokenService.java` | 생성 | 필수 Claim을 포함한 Access Token 발급 |
| `backend/src/main/java/com/realtimetilegame/security/PublicAuthPathBearerTokenResolver.java` | 생성 | 로그인·재발급·로그아웃에서 만료 Bearer가 공개 흐름을 차단하지 않게 분리 |
| `backend/src/main/java/com/realtimetilegame/security/RefreshTokenProperties.java` | 생성 | Refresh TTL·Cookie 이름·Secure 설정 바인딩 |

## Backend — Auth Application·Domain·Infrastructure·Presentation

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/auth/application/AuthService.java` | 생성 | 회원가입·로그인·재발급·로그아웃 Use Case와 상태별 로그인 차단 |
| `backend/src/main/java/com/realtimetilegame/auth/application/AuthenticatedUserView.java` | 생성 | 로그인 응답용 최소 사용자 정보 분리 |
| `backend/src/main/java/com/realtimetilegame/auth/application/RefreshRotationResult.java` | 생성 | 회전된 Refresh Token과 대상 사용자를 Application 경계로 전달 |
| `backend/src/main/java/com/realtimetilegame/auth/application/RefreshTokenService.java` | 생성 | 발급·hash 조회·pessimistic lock 회전·폐기 구현 |
| `backend/src/main/java/com/realtimetilegame/auth/domain/RefreshToken.java` | 생성 | hash·만료·폐기 시각을 보관하는 Refresh Token Entity |
| `backend/src/main/java/com/realtimetilegame/auth/domain/RefreshTokenRepository.java` | 생성 | Domain Repository 계약 분리 |
| `backend/src/main/java/com/realtimetilegame/auth/infrastructure/JpaRefreshTokenRepository.java` | 생성 | Domain Repository의 JPA Adapter |
| `backend/src/main/java/com/realtimetilegame/auth/infrastructure/SpringDataRefreshTokenJpaRepository.java` | 생성 | hash 조회와 pessimistic write lock Query |
| `backend/src/main/java/com/realtimetilegame/auth/infrastructure/RefreshTokenGenerator.java` | 생성 | `SecureRandom` 기반 256bit opaque Token 생성 |
| `backend/src/main/java/com/realtimetilegame/auth/infrastructure/RefreshTokenHasher.java` | 생성 | 원문을 저장하지 않는 SHA-256 hash 계산 |
| `backend/src/main/java/com/realtimetilegame/auth/infrastructure/RefreshTokenCookieManager.java` | 생성 | HttpOnly·SameSite Strict Cookie 발급·조회·삭제 |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/AuthController.java` | 생성 | `/api/auth/register`, `login`, `reissue`, `logout` HTTP 경계 |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/dto/RegisterRequest.java` | 생성 | 회원가입 입력 정규화·Validation |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/dto/RegisterResponse.java` | 생성 | 비밀번호 없는 회원가입 응답 |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/dto/LoginRequest.java` | 생성 | 로그인 입력 Validation과 이메일 trim |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/dto/LoginResponse.java` | 생성 | Access Token·사용자·Phase 2 고정 Redirect 응답 |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/dto/ReissueResponse.java` | 생성 | 회전 후 새 Access Token만 JSON 반환 |

## Backend — User·Profile

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/user/domain/User.java` | 생성 | BCrypt hash·프로필·상태·감사 시각을 가진 User Entity |
| `backend/src/main/java/com/realtimetilegame/user/domain/UserStatus.java` | 생성 | `ACTIVE`, `BLOCKED`, `DELETED` 상태 정의 |
| `backend/src/main/java/com/realtimetilegame/user/domain/AvatarType.java` | 생성 | 허용 아바타와 안전한 변환 규칙 정의 |
| `backend/src/main/java/com/realtimetilegame/user/domain/UserRepository.java` | 생성 | User Domain Repository 계약 |
| `backend/src/main/java/com/realtimetilegame/user/infrastructure/JpaUserRepository.java` | 생성 | User Repository JPA Adapter |
| `backend/src/main/java/com/realtimetilegame/user/infrastructure/SpringDataUserJpaRepository.java` | 생성 | 이메일·닉네임 대소문자 무시 조회 |
| `backend/src/main/java/com/realtimetilegame/user/application/UserProfileService.java` | 생성 | 인증 사용자 프로필 조회·원자적 수정·중복 처리 |
| `backend/src/main/java/com/realtimetilegame/user/presentation/MyProfileController.java` | 생성 | JWT subject 기반 GET `/api/me`, PATCH `/api/me/profile` |
| `backend/src/main/java/com/realtimetilegame/user/presentation/dto/MyProfileResponse.java` | 생성 | 사용자와 Phase 2 전적·Active Session 기본값 응답 |
| `backend/src/main/java/com/realtimetilegame/user/presentation/dto/ProfileUpdateRequest.java` | 생성 | 닉네임·아바타 수정 Validation |
| `backend/src/main/java/com/realtimetilegame/user/presentation/dto/ProfileUpdateResponse.java` | 생성 | 수정 완료 프로필 최소 응답 |

## Backend — Tests

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/test/java/com/realtimetilegame/support/DatabaseCleanup.java` | 생성 | 인증 통합 테스트 간 FK 순서를 지키는 DB 초기화 지원 |
| `backend/src/test/java/com/realtimetilegame/auth/AuthApiIntegrationTest.java` | 생성 | AUTH-001~008·010 및 BCrypt·회전·멱등 로그아웃·상태 차단 검증 |
| `backend/src/test/java/com/realtimetilegame/auth/RefreshTokenConcurrencyIntegrationTest.java` | 생성 | 동일 Refresh Token 동시 회전 시 정확히 하나만 성공 검증 |
| `backend/src/test/java/com/realtimetilegame/security/JwtSecurityIntegrationTest.java` | 생성 | 서명·만료·issuer·audience·subject·role·공통 401 검증 |
| `backend/src/test/java/com/realtimetilegame/user/ProfileApiIntegrationTest.java` | 생성 | 프로필 조회·수정·중복·잘못된 아바타 원자성 검증 |
| `backend/src/test/java/com/realtimetilegame/persistence/MigrationAndRepositoryIntegrationTest.java` | 생성 | Flyway·ddl validate·대소문자 조회·Refresh hash 저장 검증 |

## Frontend — API·State·Routing·Types

| 경로 | 변경 | 이유 |
|---|---|---|
| `frontend/src/api/httpClient.ts` | 수정 | credentials, Bearer Header, 401 Single Flight, 최대 1회 재시도 구현 |
| `frontend/src/api/apiError.ts` | 생성 | 공통 API 오류 코드·안전한 사용자 메시지 추출 |
| `frontend/src/api/authApi.ts` | 생성 | 회원가입·로그인·재발급·로그아웃 API 분리 |
| `frontend/src/api/profileApi.ts` | 생성 | 프로필 조회·수정 API 분리 |
| `frontend/src/types/auth.ts` | 생성 | 인증·프로필 Request·Response·Store 타입 정의 |
| `frontend/src/stores/pinia.ts` | 생성 | Store와 Router Guard가 공유하는 Pinia 인스턴스 |
| `frontend/src/stores/auth.ts` | 생성 | 메모리 Token·로그인·세션 복구·프로필·로그아웃 상태 관리 |
| `frontend/src/router/index.ts` | 수정 | `/health`, `/login`, `/register`, `/profile`, 인증·guestOnly Guard |
| `frontend/src/main.ts` | 수정 | shared Pinia와 HTTP 인증 Hook 연결 |

## Frontend — UI

| 경로 | 변경 | 이유 |
|---|---|---|
| `frontend/src/App.vue` | 수정 | Phase 2 표시와 상태·로그인·프로필 Navigation |
| `frontend/src/views/LoginView.vue` | 생성 | 로그인 Form·중복 제출 차단·오류·이동 |
| `frontend/src/views/RegisterView.vue` | 생성 | 회원가입 Form·비밀번호 확인 Client 차단·오류·이동 |
| `frontend/src/views/ProfileView.vue` | 생성 | 프로필 조회·닉네임·아바타·전적 기본값·로그아웃 UI |
| `frontend/src/assets/main.css` | 수정 | 기존 Health 디자인 언어를 유지한 인증·프로필 화면 스타일 |

## Frontend — Tests

| 경로 | 변경 | 이유 |
|---|---|---|
| `frontend/src/__tests__/App.spec.ts` | 수정 | Pinia 적용과 Phase 2 Shell 표시 검증 |
| `frontend/src/__tests__/AuthStore.spec.ts` | 생성 | 로그인·복구 Single Flight·익명 복구·로그아웃·프로필 상태 검증 |
| `frontend/src/__tests__/HttpClientAuthentication.spec.ts` | 생성 | Header·동시 401·재발급 실패·공개 API·무한 재시도 차단 검증 |
| `frontend/src/__tests__/AuthenticationViews.spec.ts` | 생성 | 로그인·회원가입·프로필 화면 성공·실패 흐름 검증 |
| `frontend/src/__tests__/RouterAuthenticationGuard.spec.ts` | 생성 | 보호 Route와 guestOnly Redirect 검증 |

## Documents

| 경로 | 변경 | 이유 |
|---|---|---|
| `docs/Phase2_Changed_Files.md` | 생성 | Patch 상대 경로와 파일별 변경 이유 추적 |
| `docs/Phase2_Auth_Flow_And_Security_Decisions.md` | 생성 | JWT·Refresh Token·Cookie·Single Flight 보안 결정 기록 |
| `docs/Phase2_Test_Case_Traceability.md` | 생성 | AUTH-001~010, 추가 Backend·Frontend 테스트 연결 |
| `docs/Phase2_Direct_Verification_Guide.md` | 생성 | Java 17·MySQL·브라우저 직접 검증 절차 |
| `docs/Phase2_Completion_Report.md` | 생성 | 실제 자동 테스트, 미실행 항목, 한계와 최종 판정 기록 |

## 명시적 무변경

```text
backend/src/main/java/com/realtimetilegame/game/domain/**
backend/src/main/java/com/realtimetilegame/websocket/**
backend/src/test/java/com/realtimetilegame/game/**
backend/src/test/java/com/realtimetilegame/websocket/WebSocketHealthIntegrationTest.java
frontend/package.json
frontend/package-lock.json
frontend/src/realtime/**
compose.yaml
```

---

# Phase 2 최종 검수 보완 변경 파일 — 2026-07-15

작업: `Phase 2 Active User Boundary And Logout Reliability Fix`

기존 Phase 2 Patch의 75개 파일 기록은 위에 유지한다. 이번 보완 Patch에는 아래 수정·생성 파일 21개만 포함한다.

## Backend — Active User 공통 인증 경계

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/config/SecurityConfiguration.java` | 수정 | JWT 인증 후 모든 보호 REST 요청에 활성 사용자 공통 Filter 적용 |
| `backend/src/main/java/com/realtimetilegame/security/ActiveUserAuthorizationFilter.java` | 생성 | 현재 DB 사용자가 ACTIVE인지 공통 경계에서 확인하고 BLOCKED/DELETED 즉시 차단 |
| `backend/src/main/java/com/realtimetilegame/security/RestSecurityErrorWriter.java` | 생성 | Filter·EntryPoint·DeniedHandler가 동일한 공통 JSON 오류 계약을 사용 |
| `backend/src/main/java/com/realtimetilegame/security/RestAuthenticationEntryPoint.java` | 수정 | 공통 Security 오류 Writer 사용 |
| `backend/src/main/java/com/realtimetilegame/security/RestAccessDeniedHandler.java` | 수정 | 공통 Security 오류 Writer 사용 |

## Backend — JWT와 Refresh Cookie

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/main/java/com/realtimetilegame/security/JwtConfiguration.java` | 수정 | 필수 Claim Validator를 Decoder에 연결 |
| `backend/src/main/java/com/realtimetilegame/security/RequiredJwtClaimsValidator.java` | 생성 | `iat`, `exp`, `jti`, 시간 관계와 미래 발급 시각 검증 |
| `backend/src/main/java/com/realtimetilegame/auth/presentation/AuthController.java` | 수정 | 만료·잘못된 Refresh Token 실패 응답에서도 Cookie 삭제 |

## Backend — 회귀 테스트

| 경로 | 변경 | 이유 |
|---|---|---|
| `backend/src/test/java/com/realtimetilegame/security/ActiveUserAuthorizationIntegrationTest.java` | 생성 | 기존 Access Token 보유 BLOCKED/DELETED 사용자와 공용 경로 회귀 6개 검증 |
| `backend/src/test/java/com/realtimetilegame/security/JwtSecurityIntegrationTest.java` | 수정 | JWT 필수 Claim·시간 관계 보완 테스트 6개 추가 |
| `backend/src/test/java/com/realtimetilegame/auth/AuthApiIntegrationTest.java` | 수정 | 만료·잘못된 Refresh Cookie 삭제 테스트 2개 추가 |

## Frontend — 로그아웃 신뢰성

| 경로 | 변경 | 이유 |
|---|---|---|
| `frontend/src/stores/auth.ts` | 수정 | 서버 로그아웃 성공 시에만 인증 상태 제거, 실패 시 유지·안전한 오류·Single Flight 적용 |
| `frontend/src/views/ProfileView.vue` | 수정 | 로그아웃 실패 시 리다이렉트 금지, 중복 클릭 차단과 진행 상태 표시 |
| `frontend/src/__tests__/AuthStore.spec.ts` | 수정 | 성공·실패 상태와 중복 요청 회귀 테스트 추가 |
| `frontend/src/__tests__/AuthenticationViews.spec.ts` | 수정 | 실패 메시지·리다이렉트 금지·성공 리다이렉트 테스트 추가 |

## Documents

| 경로 | 변경 | 이유 |
|---|---|---|
| `README.md` | 수정 | 최종 보안 경계와 테스트 기준 갱신 |
| `docs/Phase2_Changed_Files.md` | 수정 | 이번 보완 Patch 파일 추적성 추가 |
| `docs/Phase2_Auth_Flow_And_Security_Decisions.md` | 수정 | 활성 사용자 경계·필수 Claim·로그아웃 실패 정책 기록 |
| `docs/Phase2_Test_Case_Traceability.md` | 수정 | 신규 Backend 14개·Frontend 5개 테스트 추적 추가 |
| `docs/Phase2_Direct_Verification_Guide.md` | 수정 | Java 17·MySQL·브라우저 최종 검증 절차 갱신 |
| `docs/Phase2_Completion_Report.md` | 수정 | 전체 156/34 테스트와 최종 조건부 판정 반영 |

## 변경하지 않은 범위

```text
Phase 1 규칙 엔진
WebSocket endpoint 및 사용자 인증
DB Migration과 Entity
REST 요청·응답 계약
방·로비·GameState
pom.xml
frontend/package-lock.json
npm Dependency
```

---

# Phase 2 최종 증거·문서 정합성 변경 파일 — 2026-07-15

작업: `Phase 2 Final Evidence And Documentation Consistency Cleanup`

이번 Patch는 생산 인증 기능을 재설계하지 않고 테스트 증명력과 최종 문서 기준만 정리한다.

| 경로 | 변경 | 이유 |
|---|---|---|
| `README.md` | 수정 | 현재 자동 검증 기준을 Backend 156·Frontend 34 하나로 통일하고 구형 결과 구역 제거 |
| `backend/src/test/java/com/realtimetilegame/security/JwtSecurityIntegrationTest.java` | 수정 | 만료되지 않은 미래 Token으로 `exp <= iat` 전용 Validator를 직접 검증 |
| `docs/Phase2_Auth_Flow_And_Security_Decisions.md` | 수정 | 활성 사용자 경계·JWT 필수 Claim·로그아웃 신뢰성·Cookie 실패 삭제를 기존 절에 통합 |
| `docs/Phase2_Changed_Files.md` | 수정 | Phase 2 세 Patch의 파일 추적성과 최종 156/34 기준 명시 |
| `docs/Phase2_Completion_Report.md` | 수정 | 초기 보고와 보완 보고를 합친 단일 최종 보고서로 재작성 |
| `docs/Phase2_Direct_Verification_Guide.md` | 수정 | 구형 검증 기준과 중복 절차를 제거하고 156/34 및 최종 시나리오로 통합 |
| `docs/Phase2_Test_Case_Traceability.md` | 수정 | 구형 로그아웃 실패 테스트를 제거하고 최종 정책·테스트 집계로 통합 |

## 이번 Patch의 명시적 무변경

```text
backend/src/main/**
backend/src/main/resources/db/migration/**
backend/src/main/java/com/realtimetilegame/game/domain/**
backend/src/main/java/com/realtimetilegame/websocket/**
frontend/src/**
frontend/package.json
frontend/package-lock.json
pom.xml
```

