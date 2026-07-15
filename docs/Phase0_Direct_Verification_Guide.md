# Phase 0 직접 검증 가이드

작성 기준: 2026-07-14  
대상: Phase 0 Final Stabilization patch 적용 상태

## 1. 사전 준비

필수:

- Java 17 이상
- Node.js 22 LTS 권장
- 지원 최소 범위: Node.js 20.19 이상 또는 22.12 이상
- Docker Desktop 또는 로컬 MySQL 8.x

Node 20으로 강제 하향해 문제를 우회하지 않는다.

## 2. Docker `.env`와 Spring Boot 환경변수 구분

프로젝트 루트의 `.env`는 기본적으로 Docker Compose가 읽는다.

```text
MYSQL_DATABASE
MYSQL_USER
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
```

Spring Boot를 IntelliJ 또는 터미널에서 직접 실행할 때 루트 `.env`가 자동으로 주입되는 것은 아니다. Backend에는 다음 환경변수를 별도로 전달한다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
```

중요:

```text
MYSQL_USER     == DB_USERNAME
MYSQL_PASSWORD == DB_PASSWORD
```

두 비밀번호가 다르면 MySQL container는 healthy여도 Spring Boot 연결은 실패한다.

## 3. 환경변수 준비

프로젝트 루트에서 `.env.example`을 `.env`로 복사한다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

`.env`의 MySQL 비밀번호를 로컬 값으로 변경한다. `.env`는 Git에 올리지 않는다.

## 4. MySQL 실행

```bash
docker compose up -d mysql
docker compose ps
```

정상 기준:

```text
realtime-tile-game-mysql: healthy
container port: 3306
```

### 로컬 3306 포트가 이미 사용 중인 경우

`compose.yaml`의 host port만 변경한다.

```yaml
ports:
  - "3307:3306"
```

Backend DB URL도 같은 host port를 사용한다.

```text
jdbc:mysql://localhost:3307/realtime_tile_game?...
```

Container 내부 포트 `3306`은 변경하지 않는다.

### 기존 MySQL volume 주의

`MYSQL_PASSWORD` 같은 초기화 환경변수는 데이터 volume이 처음 만들어질 때 적용된다. 이미 생성된 volume에서 `.env` 비밀번호만 바꿔도 기존 DB 사용자 비밀번호가 자동 변경되지 않는다.

선택지는 다음과 같다.

1. 기존 데이터가 필요하면 MySQL에서 `ALTER USER`로 비밀번호를 변경한다.
2. 개발 데이터를 삭제해도 된다면 다음 명령으로 volume을 재생성한다.

```bash
docker compose down -v
docker compose up -d mysql
```

`-v`는 DB 데이터를 삭제하므로 반드시 의도한 경우에만 사용한다.

## 5. Backend 환경변수와 실행

### Windows PowerShell

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/realtime_tile_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
$env:DB_USERNAME="rtg_user"
$env:DB_PASSWORD=".env의 MYSQL_PASSWORD와 동일한 값"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"

cd backend
.\mvnw.cmd spring-boot:run
```

3307을 사용한다면 `DB_URL`의 포트도 3307로 바꾼다.

### macOS/Linux

```bash
export DB_URL='jdbc:mysql://localhost:3306/realtime_tile_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME='rtg_user'
export DB_PASSWORD='.env의 MYSQL_PASSWORD와 동일한 값'
export CORS_ALLOWED_ORIGINS='http://localhost:5173'

cd backend
./mvnw spring-boot:run
```

## 6. REST와 MySQL 확인

브라우저 또는 터미널에서 확인한다.

```text
http://localhost:8080/api/health
```

예상 응답:

```json
{
  "success": true,
  "data": {
    "application": "realtime-tile-game-backend",
    "status": "UP",
    "database": "UP"
  },
  "timestamp": "..."
}
```

인증 경계 확인:

```text
GET http://localhost:8080/api/private-probe
```

예상:

```text
HTTP 401
error.code = AUTHENTICATION_REQUIRED
```

## 7. package-lock registry 검증

프로젝트 외부 PC 호환성을 위해 내부 전용 registry 문자열이 없어야 한다.

Windows PowerShell:

```powershell
Select-String `
  -Path .\frontend\package-lock.json `
  -Pattern "internal.api.openai.org|applied-caas-gateway"
```

정상 결과:

```text
출력 없음
```

공식 registry 확인:

```powershell
Select-String `
  -Path .\frontend\package-lock.json `
  -Pattern "https://registry.npmjs.org/" |
  Select-Object -First 5
```

## 8. Frontend 설치와 실행

새 터미널에서:

### Windows PowerShell

```powershell
cd frontend
Copy-Item .env.example .env.local
node --version
npm ci
npm run check
npm run dev
```

### macOS/Linux

```bash
cd frontend
cp .env.example .env.local
node --version
npm ci
npm run check
npm run dev
```

브라우저:

```text
http://localhost:5173
```

초기 정상 기준:

- Backend REST: 정상
- MySQL: 정상
- WebSocket / STOMP: 정상
- 상단 문구: 모든 연결 정상

## 9. WebSocket 수동 재연결 검증

Chrome DevTools의 `Console`과 `Network > WS`를 열고 진행한다.

### 9-1. 최초 연결

1. 페이지를 새로고침한다.
2. WebSocket 상태가 `연결 중`에서 `정상`으로 바뀌는지 확인한다.
3. 안정 상태에서 활성 WebSocket 연결이 한 개뿐인지 확인한다.

### 9-2. 수동 재연결

1. `WebSocket 다시 연결`을 누른다.
2. 버튼이 `WebSocket 연결 중`으로 바뀌며 비활성화되는지 확인한다.
3. 기존 WebSocket이 종료된 뒤 새 WebSocket이 연결되는지 확인한다.
4. 상태가 다시 `정상`으로 바뀌는지 확인한다.
5. Console Error가 없는지 확인한다.

### 9-3. 3회 반복

수동 재연결을 총 3회 반복한다.

매번 다음을 확인한다.

```text
CONNECTING에 고정되지 않음
최종 상태 CONNECTED
안정 상태 활성 WebSocket 한 개
health 응답 중복 수신 없음
Console Error 0
```

### 9-4. 빠른 연속 클릭

재연결 버튼을 빠르게 여러 번 눌러본다.

정상 기준:

- 첫 클릭 직후 버튼이 비활성화된다.
- 중복 deactivate/activate가 발생하지 않는다.
- 새 subscription이 한 개만 생성된다.
- 동일 health 응답이 중복 표시되지 않는다.

## 10. 자동 테스트

Backend:

```bash
cd backend
./mvnw test
```

Windows:

```powershell
cd backend
.\mvnw.cmd test
```

현재 기준 통과 목표:

```text
Tests run: 4
Failures: 0
Errors: 0
```

Frontend:

```bash
cd frontend
npm ci
npm run check
```

현재 기준 통과 목표:

```text
Test Files: 3 passed
Tests: 11 passed
TypeScript: success
Vite production build: success
```

## 11. CORS 확인

프론트 주소가 `http://localhost:5173`일 때 브라우저 개발자 도구 Network에서 `/api/health`가 CORS 오류 없이 200이어야 한다.

다른 포트를 사용한다면 Backend의 `CORS_ALLOWED_ORIGINS`와 Frontend 주소를 함께 변경한다.

## 12. 종료

```bash
docker compose down
```

DB 데이터까지 삭제하려면:

```bash
docker compose down -v
```

## 13. 완료 보고 형식

```text
OS:
Java:
Node:
Docker/MySQL: healthy/실패
Backend test: 4/4 또는 실제 결과
Frontend test: 11/11 또는 실제 결과
TypeScript check: 성공/실패
Production build: 성공/실패
package-lock internal registry: 0건/발견
/api/health: 성공/실패
Database UP: 성공/실패
최초 WebSocket: 성공/실패
수동 재연결: 성공/실패
재연결 3회: 성공/실패
빠른 연속 클릭 중복 방지: 성공/실패
중복 subscription 수신: 없음/있음
Console Error/Warning:
최종 판정: 통과/재작업
```
