# Phase 2 테스트 케이스 추적성

작성 기준: 2026-07-15  
최종 기준: Backend 156개 / Frontend Vitest 34개

## 1. Backend Test Matrix

| Test ID | 요구 규칙 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|---|
| AUTH-001 | 정상 회원가입, 정규화, BCrypt | `AuthApiIntegrationTest` | `auth001RegistersUserWithNormalizedValuesAndBcryptPassword` | 통과 |
| AUTH-002 | 이메일 중복 409 | `AuthApiIntegrationTest` | `auth002RejectsDuplicateEmailCaseInsensitively` | 통과 |
| AUTH-003 | 닉네임 중복 409 | `AuthApiIntegrationTest` | `auth003RejectsDuplicateNicknameCaseInsensitively` | 통과 |
| AUTH-004 | 비밀번호 확인 불일치 | `AuthApiIntegrationTest` | `auth004RejectsPasswordConfirmationMismatch` | 통과 |
| AUTH-005 | 로그인·JWT·HttpOnly Cookie·hash 저장 | `AuthApiIntegrationTest` | `auth005LoginReturnsAccessTokenAndHttpOnlyRefreshCookieWithHashedStorage` | 통과 |
| AUTH-006 | 잘못된 이메일·비밀번호 동일 오류 | `AuthApiIntegrationTest` | `auth006UsesSameErrorForUnknownEmailAndWrongPassword` | 통과 |
| AUTH-007 | 만료 Refresh Token 차단 | `AuthApiIntegrationTest` | `auth007RejectsExpiredRefreshToken` | 통과 |
| AUTH-008 | 로그아웃 후 재발급 차단 | `AuthApiIntegrationTest` | `auth008LogoutRevokesRefreshTokenAndBlocksReissue` | 통과 |
| AUTH-009 | 닉네임·아바타 원자적 수정 | `ProfileApiIntegrationTest` | `auth009UpdatesNicknameAndAvatarAtomically` | 통과 |
| AUTH-010 | 삭제 사용자 로그인 차단 | `AuthApiIntegrationTest` | `auth010DeletedUserCannotLogin` | 통과 |

## 2. Backend 인증·정합성 테스트

| 요구사항 | 테스트 클래스·메서드 | 결과 |
|---|---|---|
| Password Policy | `AuthApiIntegrationTest.rejectsPasswordThatDoesNotMeetPolicy` | 통과 |
| Refresh 회전·이전 Token 재사용 차단 | `AuthApiIntegrationTest.reissueRotatesCookieRevokesPreviousTokenAndRejectsReuse` | 통과 |
| 로그아웃 멱등성·만료 Bearer 무시 | `AuthApiIntegrationTest.logoutIsIdempotentAndIgnoresExpiredBearerHeader` | 통과 |
| 차단 사용자 로그인 거부 | `AuthApiIntegrationTest.blockedUserCannotLogin` | 통과 |
| 동시 재발급 정확히 하나 성공 | `RefreshTokenConcurrencyIntegrationTest.concurrentReissueAllowsExactlyOneRotation` | 통과 |
| 정상 JWT로 `/api/me` | `JwtSecurityIntegrationTest.validAccessTokenCanReadMyProfile` | 통과 |
| Token 없음 공통 401 | `JwtSecurityIntegrationTest.missingAccessTokenReturnsCommonUnauthorizedJson` | 통과 |
| 잘못된 서명 | `JwtSecurityIntegrationTest.wrongSignatureIsRejected` | 통과 |
| 만료 Access Token | `JwtSecurityIntegrationTest.expiredAccessTokenIsRejected` | 통과 |
| 잘못된 issuer | `JwtSecurityIntegrationTest.wrongIssuerIsRejected` | 통과 |
| 잘못된 audience | `JwtSecurityIntegrationTest.wrongAudienceIsRejected` | 통과 |
| 숫자가 아닌 subject | `JwtSecurityIntegrationTest.nonNumericSubjectIsRejected` | 통과 |
| role 누락 | `JwtSecurityIntegrationTest.missingRoleClaimIsRejected` | 통과 |
| Phase 2 기본 프로필 응답 | `ProfileApiIntegrationTest.profileReturnsPhase2Defaults` | 통과 |
| 자기 닉네임 유지 | `ProfileApiIntegrationTest.keepingOwnNicknameSucceeds` | 통과 |
| 프로필 닉네임 중복 | `ProfileApiIntegrationTest.duplicateNicknameIsRejected` | 통과 |
| 잘못된 아바타·원자성 | `ProfileApiIntegrationTest.invalidAvatarIsRejectedWithoutChangingNickname` | 통과 |
| Flyway + ddl validate | `MigrationAndRepositoryIntegrationTest.migrationCreatesRequiredTablesAndHibernateValidateStarts` | 통과 |
| 대소문자 무시 Repository | `MigrationAndRepositoryIntegrationTest.userRepositoryLooksUpEmailAndNicknameIgnoringCase` | 통과 |
| Refresh 원문 미저장 | `MigrationAndRepositoryIntegrationTest.refreshRepositoryStoresOnlyHashValue` | 통과 |

## 3. Backend 최종 보안 보완 테스트

| 요구사항 | 테스트 클래스 | 테스트 메서드 | 결과 |
|---|---|---|---|
| 발급 후 BLOCKED 전환 즉시 차단 | `ActiveUserAuthorizationIntegrationTest` | `issuedAccessTokenIsRejectedAfterUserBecomesBlocked` | 통과 |
| 발급 후 DELETED 전환 즉시 차단 | `ActiveUserAuthorizationIntegrationTest` | `issuedAccessTokenIsRejectedAfterUserBecomesDeleted` | 통과 |
| ACTIVE 사용자 기존 Token 정상 | `ActiveUserAuthorizationIntegrationTest` | `activeUserAccessTokenStillSucceeds` | 통과 |
| BLOCKED 프로필 수정 차단 | `ActiveUserAuthorizationIntegrationTest` | `blockedUserCannotUpdateProfile` | 통과 |
| DELETED 프로필 수정 차단 | `ActiveUserAuthorizationIntegrationTest` | `deletedUserCannotUpdateProfile` | 통과 |
| Health·공개 Auth 경로 보존 | `ActiveUserAuthorizationIntegrationTest` | `existingHealthAndPublicAuthPathsRemainPublic` | 통과 |
| `iat` 누락 차단 | `JwtSecurityIntegrationTest` | `missingIssuedAtClaimIsRejected` | 통과 |
| `exp` 누락 차단 | `JwtSecurityIntegrationTest` | `missingExpirationClaimIsRejected` | 통과 |
| `jti` 누락 차단 | `JwtSecurityIntegrationTest` | `missingJwtIdClaimIsRejected` | 통과 |
| blank `jti` 차단 | `JwtSecurityIntegrationTest` | `blankJwtIdClaimIsRejected` | 통과 |
| 만료되지 않은 Token의 `exp <= iat` 차단 | `JwtSecurityIntegrationTest` | `expirationBeforeIssuedAtIsRejected` | 통과 |
| Clock Skew 초과 미래 `iat` 차단 | `JwtSecurityIntegrationTest` | `issuedAtBeyondClockSkewIsRejected` | 통과 |
| 만료 Refresh Cookie 삭제 | `AuthApiIntegrationTest` | `expiredRefreshTokenClearsCookie` | 통과 |
| 잘못된 Refresh Cookie 삭제 | `AuthApiIntegrationTest` | `invalidRefreshTokenClearsCookie` | 통과 |

## 4. 기존 Backend 회귀

| 범위 | 테스트 수 | 결과 |
|---|---:|---|
| Phase 1 순수 규칙 엔진 | 108 | 통과 |
| Health REST | 3 | 통과 |
| Health WebSocket/STOMP | 1 | 통과 |
| 기존 합계 | 112 | 통과 |

## 5. Frontend 테스트

| 요구사항 | 테스트 파일 | 대표 테스트 | 결과 |
|---|---|---|---|
| 로그인 성공 Token·User 메모리 저장 | `AuthStore.spec.ts` | `stores the login access token and user in memory state` | 통과 |
| 새로고침 복구·중복 방지 | `AuthStore.spec.ts` | `restores the session once and loads the profile` | 통과 |
| Refresh Cookie 없음 익명 처리 | `AuthStore.spec.ts` | `treats a missing refresh cookie as an ordinary anonymous session` | 통과 |
| 로그아웃 성공 시 상태 제거 | `AuthStore.spec.ts` | `logoutSuccessClearsAuthentication` | 통과 |
| 로그아웃 실패 시 상태 유지 | `AuthStore.spec.ts` | `logoutFailureKeepsAuthentication` | 통과 |
| 연속 로그아웃 요청 Single Flight | `AuthStore.spec.ts` | `repeatedLogoutClickDoesNotSendDuplicateRequest` | 통과 |
| 프로필 상태 동기화 | `AuthStore.spec.ts` | `updates profile and user state together` | 통과 |
| Authorization Header | `HttpClientAuthentication.spec.ts` | `adds the in-memory access token to protected requests` | 통과 |
| 동시 401 Single Flight | `HttpClientAuthentication.spec.ts` | `uses one refresh request for concurrent 401 responses and retries each request once` | 통과 |
| 재발급 실패 인증 제거 | `HttpClientAuthentication.spec.ts` | `clears authentication when refresh fails` | 통과 |
| 로그인 실패 자동 재발급 금지 | `HttpClientAuthentication.spec.ts` | `does not refresh public login failures` | 통과 |
| 무한 재시도 차단 | `HttpClientAuthentication.spec.ts` | `never retries the same protected request more than once` | 통과 |
| `/profile` Guard | `RouterAuthenticationGuard.spec.ts` | 익명 사용자 로그인 이동 | 통과 |
| guestOnly Guard | `RouterAuthenticationGuard.spec.ts` | 인증 사용자 프로필 이동 | 통과 |
| 로그인 성공 화면 이동 | `AuthenticationViews.spec.ts` | `logs in and moves to the profile screen` | 통과 |
| 로그인 실패 표시 | `AuthenticationViews.spec.ts` | `shows a safe login failure message` | 통과 |
| 회원가입 성공 이동 | `AuthenticationViews.spec.ts` | `moves to login after successful registration` | 통과 |
| 비밀번호 확인 Client 차단 | `AuthenticationViews.spec.ts` | `blocks registration locally when password confirmation differs` | 통과 |
| 프로필 조회 | `AuthenticationViews.spec.ts` | `loads a profile when the authenticated screen has no cached profile` | 통과 |
| 프로필 수정 | `AuthenticationViews.spec.ts` | `updates nickname and avatar from the profile screen` | 통과 |
| 로그아웃 실패 안전 메시지 | `AuthenticationViews.spec.ts` | `logoutFailureShowsSafeMessage` | 통과 |
| 로그아웃 실패 리다이렉트 금지 | `AuthenticationViews.spec.ts` | `logoutFailureDoesNotRedirect` | 통과 |
| 성공 로그아웃 후 로그인 이동 | `AuthenticationViews.spec.ts` | `successfulLogoutRedirectsToLogin` | 통과 |

## 6. 최종 집계

```text
Backend 기존 Phase 0·1 회귀: 112
Backend Phase 2 인증·프로필·보안: 44
Backend 전체: 156
Failures: 0
Errors: 0
Skipped: 0

Frontend Vitest 전체: 34
Failures: 0
TypeScript: 통과
Production Build: 통과
```
