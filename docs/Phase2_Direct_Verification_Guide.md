# Phase 2 직접 실행·검증 가이드

작성 기준: 2026-07-15

## 1. 사전 조건

- Java 17
- Node.js 22 LTS
- Docker Desktop
- MySQL 8.4 컨테이너
- 실제 비밀값은 로컬 환경변수로만 설정

Patch에는 `.env`, `target`, `node_modules`, `dist`, Token, Cookie가 포함되지 않는다.

## 2. 환경변수

PowerShell 예시:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/realtime_tile_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
$env:DB_USERNAME="rtg_user"
$env:DB_PASSWORD="<local-db-secret>"
$env:JWT_ACCESS_SECRET_BASE64="<base64-encoded-32-byte-or-longer-secret>"
$env:AUTH_REFRESH_COOKIE_SECURE="false"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
```

- JWT Secret 원문을 Git, Screenshot, 완료 보고서에 남기지 않는다.
- 로컬 HTTP는 Cookie Secure를 `false`로 사용한다.
- HTTPS 환경에서는 `true`로 바꾼다.
- Docker `MYSQL_PASSWORD`와 Spring `DB_PASSWORD`는 일치해야 한다.
- 기존 MySQL 볼륨은 환경변수 비밀번호 변경을 자동 반영하지 않는다.

## 3. Backend 자동 테스트

```powershell
cd .\backend
java -version
.\mvnw.cmd clean test
```

정상 기준:

```text
Tests run: 156
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

`target/surefire-reports`에서 Auth, Active User, JWT, Profile, Migration, Rule Engine, Health, WebSocket 보고서를 확인한다.

## 4. Frontend 자동 테스트

```powershell
cd .\frontend
node --version
npm ci
npm run check
```

정상 기준:

```text
Vitest: 34개 통과
TypeScript: 통과
Production Build: 통과
```

Access Token 영속 저장 금지 검색:

```powershell
Get-ChildItem .\src -Recurse -File |
Select-String -Pattern "localStorage|sessionStorage|IndexedDB"
```

인증 Token 저장 목적의 결과가 없어야 한다.

## 5. MySQL과 애플리케이션 실행

프로젝트 루트:

```powershell
docker compose up -d mysql
docker compose ps
```

Backend:

```powershell
cd .\backend
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd .\frontend
npm run dev
```

## 6. Scenario A — 회원가입과 로그인

1. `/register` 접속
2. 신규 이메일·닉네임·정상 비밀번호 입력
3. 회원가입 201 확인
4. MySQL `users.password`가 BCrypt 형식인지 확인
5. 평문 비밀번호가 DB에 없는지 확인
6. `/login`에서 로그인
7. 응답 JSON에 Access Token이 있고 Refresh Token은 없는지 확인
8. Browser Application/Storage에서 `rtg_refresh` 확인
9. Cookie가 HttpOnly, SameSite Strict, Path `/api/auth`인지 확인
10. `/profile` 이동 확인

## 7. Scenario B — 새로고침 복구

1. 로그인 상태에서 `/profile` 새로고침
2. Network에서 `POST /api/auth/reissue` 1회 확인
3. 이어서 `GET /api/me` 확인
4. 프로필이 유지되는지 확인
5. Access Token이 브라우저 영속 저장소에 없는지 확인

## 8. Scenario C — 중복과 Validation

- 같은 이메일 회원가입 → `409 EMAIL_ALREADY_EXISTS`
- 같은 닉네임 회원가입 → `409 NICKNAME_ALREADY_EXISTS`
- 비밀번호 확인 불일치 → `400 PASSWORD_CONFIRM_MISMATCH` 또는 Client 제출 차단
- 잘못된 비밀번호 → `401 INVALID_CREDENTIALS`
- 잘못된 아바타 → `400 INVALID_AVATAR_TYPE`

## 9. Scenario D — Refresh Token 회전

1. 로그인 후 Cookie A 확보
2. `POST /api/auth/reissue`
3. Cookie B로 교체됐는지 확인
4. DB에서 A hash 행의 `revoked_at` 확인
5. A를 다시 사용하면 401인지 확인
6. DB와 로그에 A/B 원문이 없는지 확인

Cookie 값은 외부 문서나 Screenshot에 포함하지 않는다.

## 10. Scenario E — 정상 로그아웃

1. 로그인 후 로그아웃
2. 204 확인
3. `rtg_refresh` 삭제 확인
4. 이전 Refresh Token 재발급 401 확인
5. `/login` 이동 확인
6. `/profile` 접근 시 `/login` 이동 확인
7. Cookie가 없는 상태에서 다시 로그아웃해도 204인지 확인

## 11. Scenario F — 프로필

1. 닉네임과 아바타 변경
2. 저장 성공 확인
3. 새로고침 후 변경값 유지 확인
4. 다른 사용자의 닉네임으로 변경 → 409 확인
5. 자신의 현재 닉네임 유지 저장 → 성공 확인

## 12. Scenario G — 기존 Access Token 사용자 상태 차단

1. 정상 사용자로 로그인하고 Access Token을 확보한다.
2. MySQL에서 해당 사용자의 `status`를 `BLOCKED`로 변경한다.
3. 기존 Access Token으로 `GET /api/me`를 요청한다.
4. `403 USER_BLOCKED`를 확인한다.
5. 같은 Token으로 `PATCH /api/me/profile`도 403인지 확인한다.
6. 상태를 `ACTIVE`로 복구하고 동일 Token으로 정상 접근되는지 확인한다.
7. 상태를 `DELETED`로 변경한다.
8. 기존 Token의 GET·PATCH가 `401 USER_DELETED`인지 확인한다.
9. 테스트 후 상태를 원복하거나 테스트 사용자를 정리한다.

JWT의 status Claim을 바꾸는 방식이 아니라 DB의 현재 상태 변경을 기준으로 확인한다.

## 13. Scenario H — 로그아웃 서버 실패

1. 로그인된 `/profile` 화면을 연다.
2. Backend를 중지하거나 브라우저 Network 차단으로 로그아웃 요청을 실패시킨다.
3. 로그아웃 버튼을 누른다.
4. `/login`으로 이동하지 않는지 확인한다.
5. 현재 프로필과 인증 UI가 유지되는지 확인한다.
6. 안전한 실패 메시지가 표시되는지 확인한다.
7. 버튼을 빠르게 여러 번 눌러 Network 요청이 하나만 발생하는지 확인한다.
8. Backend 복구 후 다시 로그아웃하여 204, Cookie 삭제, `/login` 이동을 확인한다.

## 14. Scenario I — 만료·잘못된 Refresh Cookie 정리

1. 만료됐거나 DB에 없는 테스트용 Refresh Cookie로 `/api/auth/reissue`를 요청한다.
2. `401 REFRESH_TOKEN_EXPIRED` 또는 `401 REFRESH_TOKEN_INVALID`를 확인한다.
3. 응답 `Set-Cookie`에 동일 Cookie 이름·Path와 `Max-Age=0`이 있는지 확인한다.
4. 브라우저 저장소에서 `rtg_refresh`가 제거되는지 확인한다.
5. 이후 새로고침에서 같은 잘못된 Cookie가 반복 전송되지 않는지 확인한다.

## 15. Browser 최종 체크

```text
Console Error 0
Network 무한 401 loop 없음
동시 보호 API 401 시 reissue 1회
Access Token 영속 저장 없음
Refresh Token JavaScript 접근 불가
Refresh Token JSON·로그 노출 없음
BLOCKED/DELETED 기존 Token 즉시 차단
로그아웃 실패 시 상태·화면 유지
로그아웃 성공 시 상태 제거와 /login 이동
기존 /health 정상
기존 WebSocket Health 정상
```

## 16. 최종 완료 조건

다음이 모두 확인되기 전에는 Phase 3로 이동하지 않는다.

```text
Java 17 단일 clean test 156개
Frontend Vitest 34개와 TypeScript·Production Build
실제 MySQL 8.4 V1 Migration과 ddl-auto=validate
회원가입·로그인·재발급·정상 로그아웃
새로고침 세션 복구
Cookie 보안 속성
기존 Token의 BLOCKED/DELETED 차단
로그아웃 실패 시 상태·화면 유지와 재시도
Refresh Cookie 실패 삭제
프로필 조회·수정
기존 Health REST·STOMP
```
