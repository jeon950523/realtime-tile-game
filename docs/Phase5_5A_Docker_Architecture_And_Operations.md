# Phase 5.5-A Docker Architecture And Operations

작성 기준: 2026-07-16 KST

## 1. Runtime Architecture

```text
Browser http://localhost:5173
        │
        ▼
Frontend Nginx Container :80
  ├─ /          → Vue dist + SPA fallback
  ├─ /api/**    → backend:8080/api/**
  └─ /ws        → backend:8080/ws + WebSocket Upgrade
                         │
                         ▼
                Backend Container :8080
                  ├─ REST
                  ├─ STOMP WebSocket
                  ├─ JPA / Flyway
                  └─ DB URL mysql:3306
                         │
                         ▼
                   MySQL 8.4.10 :3306
                   Named Volume
```

Compose 기본 네트워크만 사용한다. Container 간 주소는 Host IP가 아니라 Service Name을 사용한다.

## 2. 기동 순서

```text
mysql start
→ mysql health check
→ backend start
→ /api/health에서 Application + DB 확인
→ frontend start
→ / health check
```

`depends_on.condition: service_healthy`는 초기 기동 순서를 보조한다. Runtime 장애 복구는 각 Service의 `restart: unless-stopped`와 애플리케이션 재연결 로직이 담당한다.

## 3. Backend Image Boundary

Build Stage:

```text
JDK 17
Maven Wrapper
curl + unzip + SHA-512 검증
pom dependency cache
-DskipTests package
```

Runtime Stage:

```text
JRE 17
실행 JAR
curl Health Check
tzdata
Non-root app user
```

Runtime Image에는 `src`, Maven Cache, Maven 실행 파일을 복사하지 않는다.

Docker Build가 테스트를 생략하는 이유는 반복 Build 시간을 줄이기 위한 것이다. 대신 Host 또는 CI의 `./mvnw clean test`가 별도 완료 조건이다.

## 4. Frontend Image Boundary

Build Stage:

```text
Node 22
package-lock.json
npm ci
VITE_API_BASE_URL=""
VITE_WS_URL=""
npm run build
```

Runtime Stage:

```text
Nginx
Vue dist
Nginx default.conf
```

Runtime Image에는 Source, `node_modules`, npm Cache, Frontend Secret이 없다.

## 5. Endpoint Resolution

API:

```text
명시 VITE_API_BASE_URL 존재 → 해당 값
빈 값 또는 미설정          → ""
Axios /api 요청            → 현재 Browser Origin
```

WebSocket:

```text
명시 VITE_WS_URL 존재 → 해당 값
http location          → ws://{host}/ws
https location         → wss://{host}/ws
```

이 구조로 Docker Production은 Same-origin을 사용하고 기존 PowerShell 직접 실행은 `.env.local`의 명시 URL을 유지한다.

## 6. Nginx Path Contract

```nginx
location /api/ {
    proxy_pass http://backend:8080;
}

location = /ws {
    proxy_pass http://backend:8080;
}
```

`proxy_pass`에 Trailing Slash나 URI를 붙이지 않아 원래 `/api/**`, `/ws` Path를 Backend에 그대로 전달한다.

SPA Route는 다음 계약을 사용한다.

```nginx
try_files $uri $uri/ /index.html;
```

따라서 `/login`, `/lobby`, `/health`에서 F5해도 Nginx 404가 아니라 Vue Router가 처리한다.

## 7. Secret Boundary

Runtime Secret:

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
JWT_ACCESS_SECRET_BASE64
```

흐름:

```text
로컬 .env
→ docker compose 변수 치환
→ Container Runtime Environment
```

금지:

```text
Dockerfile ARG에 Secret 전달
Dockerfile ENV에 Secret 고정
Frontend Build에 Secret 전달
.env를 Build Context나 ZIP에 포함
```

## 8. Database Volume Policy

정상 종료:

```powershell
docker compose down
```

Named Volume은 유지되므로 회원·방·게임·타일·Flyway 이력이 보존된다.

데이터 삭제:

```powershell
docker compose down -v
```

이 명령은 정상 실행 절차가 아니며 의도적 전체 초기화일 때만 사용한다. Migration 성공을 위해 Volume 삭제를 요구하면 Phase 5.5-A 실패다.

## 9. 운영 명령

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f mysql
docker compose logs -f backend
docker compose logs -f frontend
docker compose restart backend
docker compose down
```

No-cache 검증:

```powershell
docker compose build --no-cache
```

## 10. 현재 확장 한계

현재 Compose는 Backend 단일 Process만 지원한다.

이유:

```text
In-memory Game Action Replay Store
Spring Simple Broker
Process-local WebSocket Connection
```

따라서 다음은 Phase 5.5-A에 포함하지 않는다.

```text
Backend replicas > 1
Redis
External STOMP Broker
Session Affinity
Kubernetes
Classroom LAN Origin
TLS
```
