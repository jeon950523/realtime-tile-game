# Phase 2 인증 흐름 및 보안 결정

작성 기준: 2026-07-15  
기준 전체본: `phase0715-02-41-phase1-final.zip`  
적용 패치: Phase 2 인증·프로필 구현 및 최종 안정화 패치

## 1. 인증 흐름

```text
회원가입
→ 이메일·닉네임 정규화와 중복 검사
→ 비밀번호 BCrypt hash 저장
→ Token 자동 발급 없음

로그인
→ 이메일·비밀번호와 현재 사용자 상태 검증
→ JWT Access Token JSON 반환
→ opaque Refresh Token 생성
→ SHA-256 hash만 DB 저장
→ 원문은 HttpOnly Cookie로만 전달

보호 REST API
→ Frontend 메모리 Access Token을 Bearer Header로 전달
→ Spring Security Resource Server가 JWT 검증
→ ActiveUserAuthorizationFilter가 DB의 현재 User 상태 확인
→ ACTIVE 사용자만 요청 진행

새로고침
→ 메모리 Access Token 소실
→ HttpOnly Cookie로 POST /api/auth/reissue
→ 기존 Refresh Token 폐기 및 새 Token 회전
→ 새 Access Token으로 GET /api/me
→ 인증 상태 복구
```

## 2. Access Token 결정

- 형식: JWT
- 알고리즘: HS256
- 발급: Spring Security `JwtEncoder`
- 검증: Spring Security Resource Server `JwtDecoder`
- 필수 Claim: `sub`, `iss`, `aud`, `iat`, `exp`, `jti`, `role`
- subject: 영속화된 사용자 ID 문자열
- 기본 수명: 1,800초
- Frontend 저장: Pinia 메모리만
- 브라우저 영속 저장소와 Cookie 저장 금지
- Secret: `JWT_ACCESS_SECRET_BASE64` 환경변수
- Base64 decode 후 최소 32바이트 검증

Decoder 검증 범위:

```text
서명
issuer
관객 audience
숫자형 양수 subject
role = USER
iat 존재
exp 존재
jti 존재 및 blank 금지
exp > iat
iat가 허용 Clock Skew 60초를 초과해 미래가 아닌지 확인
```

JWT 문자열을 직접 분리하거나 자체 서명 파서를 만들지 않는다. Nimbus Decoder와 `OAuth2TokenValidator<Jwt>` 확장 경계를 사용한다.

실제 Secret 값은 소스, 예시 설정, 로그, 문서, Patch에 포함하지 않는다.

## 3. 현재 사용자 상태 공통 인증 경계

JWT는 발급 당시의 인증 사실을 증명하지만 현재 DB 사용자 상태를 대신하지 않는다. JWT에 status Claim을 넣거나 신뢰하지 않는다.

```text
BearerTokenAuthenticationFilter
→ JWT 서명·필수 Claim 검증
→ ActiveUserAuthorizationFilter
→ DB의 현재 User 조회
→ ACTIVE만 보호 REST 요청 진행
```

결과 정책:

```text
ACTIVE: 요청 진행
BLOCKED: 403 USER_BLOCKED
DELETED: 401 USER_DELETED
사용자 행 없음: 401 AUTHENTICATION_REQUIRED
```

상태 검사는 Controller마다 복사하지 않고 공통 Security Filter에서 수행한다. `/api/health`, 공개 Auth API, OPTIONS, `/ws/**`는 REST 활성 사용자 Filter 대상에서 제외한다. WebSocket Principal 인증은 후속 Phase 범위다.

## 4. Refresh Token 결정

- 형식: `SecureRandom` 기반 256bit opaque 값
- Cookie 이름: 기본 `rtg_refresh`
- 속성: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`
- `Secure`: 환경변수로 제어
- DB 저장: 원문이 아닌 SHA-256 hex hash만 저장
- 기본 수명: 30일
- 재발급마다 기존 Token 폐기 후 새 Token 발급
- DB pessimistic write lock으로 동일 Token의 동시 회전 직렬화
- 폐기·만료·알 수 없는 Token 재사용 불가

`REFRESH_TOKEN_INVALID`와 `REFRESH_TOKEN_EXPIRED` 응답에서도 동일 Cookie 이름·Path·보안 속성으로 `Max-Age=0`을 전송한다. 오래된 Cookie가 브라우저에서 반복 전송되는 흐름을 끊는다.

Cookie Path를 `/api/auth`로 제한해 프로필이나 향후 게임 API 요청에 Refresh Token이 불필요하게 포함되지 않게 했다.

## 5. 로그아웃 신뢰성

Backend 로그아웃은 다음 정책을 사용한다.

```text
Cookie가 있음
→ 해당 hash 조회
→ 존재하며 미폐기면 revoked_at 기록
→ Cookie Max-Age=0 삭제
→ 204

Cookie 없음·이미 폐기·알 수 없는 값
→ 상태를 추가 변경하지 않음
→ Cookie 삭제 응답
→ 204
```

로그아웃은 Access Token이 만료됐거나 잘못된 Bearer Header가 붙어 있어도 Refresh Cookie를 폐기할 수 있도록 공개 인증 경로에서 Bearer Token 해석을 생략한다.

Frontend는 서버가 204를 반환한 경우에만 Access Token·User·Profile 메모리 상태를 제거하고 `/login`으로 이동한다.

서버 오류나 네트워크 실패 시:

```text
현재 인증 상태 유지
현재 화면 유지
안전한 재시도 메시지 표시
중복 로그아웃 요청 차단
```

로그아웃 요청은 진행 중 Promise를 공유해 빠른 연속 클릭에도 서버 요청이 한 번만 발생한다.

## 6. Frontend 401 Single Flight

```text
보호 API에서 401
→ 원 요청의 재시도 marker 확인
→ 진행 중 재발급 Promise가 없으면 1회 생성
→ 동시 401 요청은 같은 Promise 대기
→ 새 Access Token을 메모리에 반영
→ 각 원 요청을 최대 1회 재시도
```

다음 요청은 자동 재발급하지 않는다.

- 회원가입
- 로그인
- 재발급 자체
- 로그아웃

재발급 실패 시 Access Token, 사용자, 프로필을 제거하고 익명 상태로 전환한다. 원 요청에는 내부 marker를 둬 무한 401 재시도를 차단한다.

## 7. 비밀번호·사용자 입력

비밀번호:

- 8~64자
- 영문·숫자·특수문자 각각 1개 이상
- 공백 금지
- BCrypt hash만 저장

이메일:

- trim 후 lowercase 저장
- 최대 255자
- 대소문자 무시 중복 검사

닉네임:

- trim
- 2~20자
- 한글·영문·숫자·언더스코어
- 대소문자 무시 중복 검사

아바타:

- `DEFAULT_01`~`DEFAULT_04`

## 8. DB와 시간 결정

- `V1__create_users_and_refresh_tokens.sql`로 최초 스키마 생성
- 운영 및 테스트 모두 Flyway 실행
- `ddl-auto=validate`
- Entity 시간 필드는 MySQL `DATETIME(6)`와 일치하도록 UTC 의미의 `LocalDateTime` 사용
- Refresh Token hash 컬럼은 Entity와 Migration 모두 `CHAR(64)`로 일치

## 9. 남은 보안 한계

Phase 2에서 의도적으로 제외한 항목:

- Refresh Token 탈취 탐지와 Token Family 전체 폐기
- 기기별 세션 관리 화면
- 비밀번호 변경 시 전체 Refresh Token 폐기
- 로그인 시도 Rate Limit
- 이메일 인증
- OAuth2
- WebSocket Handshake·STOMP 사용자 인증
- HTTPS 강제와 운영 Cookie Secure 실환경 검증

이 항목은 현재 인증 흐름에 임시로 섞지 않고 후속 작업으로 관리한다.
