# Phase 5.5-A Completion Report — FINAL

작성 기준:

```text
2026-07-16 KST
```

기준 전체본:

```text
phase0716-00-31-phase5-final-clean-source-fixed.zip
```

최종 통합 전체본:

```text
phase0716-01-57-phase5_5A-final-clean-source.zip
```

작업:

```text
Phase 5.5-A — Minimum Dockerized Local Runtime
```

## 1. 최종 구현 결과

```text
docker compose up -d --build
→ MySQL Container
→ Spring Boot Backend Container
→ Vue Production Build + Nginx Container
```

Host Port:

```text
MySQL    127.0.0.1:33307
Backend  127.0.0.1:8080
Frontend 127.0.0.1:5173
```

브라우저 통신:

```text
/api/** → Frontend Nginx → backend:8080/api/**
/ws     → Frontend Nginx → backend:8080/ws
```

## 2. Docker 구성

### Backend

- Java 17 Multi-stage Build
- Maven Wrapper 기반 Build
- Runtime에는 실행 JAR와 JRE만 포함
- Non-root `app` 사용자 실행
- `/api/health` Health Check
- Runtime Secret은 Docker Image에 Bake-in하지 않음

### Frontend

- Node 22 Multi-stage Build
- `npm ci`와 Production Build
- Nginx 정적 파일 제공
- Vue SPA History Fallback
- `/api/**` REST Reverse Proxy
- `/ws` WebSocket Upgrade Proxy
- Same-origin Endpoint Resolver
- Runtime Image에 Source·node_modules 미포함

### MySQL

- MySQL 8.4.10
- Named Volume 사용
- Host `33307` → Container `3306`
- Health Check 적용
- Flyway V1~V4 정상 적용

## 3. 자동 검증

### Backend

```text
전체 회귀 테스트 정상
Phase 5 기준 Tests run: 278
Failures: 0
Errors: 0
Skipped: 0
```

### Frontend

```text
Test Files: 14 passed
Tests: 99 passed
TypeScript: passed
Production Build: passed
```

## 4. Docker Runtime 직접 검증

```text
mysql     healthy
backend   healthy
frontend  healthy
```

Health 화면:

```text
Backend REST     정상
MySQL            정상
WebSocket/STOMP  정상
```

브라우저 통신:

```text
/api/health
→ localhost:5173 Same-origin Proxy

/ws
→ ws://localhost:5173/ws
→ CONNECTED
```

## 5. 게임 회귀 검증

Chrome 일반 창과 별도 세션을 이용해 다음을 확인했다.

```text
회원가입·로그인
2계정 로비·대기방
READY·START
Rack 14개씩 초기 분배
Pool 78
Draw
Pool 감소
Rack 증가
다음 사용자 턴 전환
Game Version 증가
상대 Rack Count만 공개
자기 Rack 상세만 공개
F5 최신 Game State 복구
WebSocket CONNECTED
```

최종 확인 화면 예시:

```text
Status       IN_PROGRESS
Turn         7
Pool         72
Game Version 6
Player A     Rack 17
Player B     Rack 17
```

## 6. 구현 중 해결한 문제

### 기존 MySQL Volume과 현재 환경변수 불일치

```text
증상:
Access denied for user
Backend Container 반복 재시작

원인:
기존 Named Volume에 저장된 MySQL 계정 비밀번호와
현재 .env의 비밀번호가 달랐음

해결:
개발 검증 Volume을 현재 환경변수 기준으로 재초기화
```

### Production STOMP Debug Callback 오류

```text
증상:
this.debug is not a function
WebSocket 연결이 CONNECTING에 머묾

해결:
Production에서도 호출 가능한 No-op debug 함수 적용
회귀 테스트 추가
```

### 공개 Health Route의 불필요한 Refresh 요청

```text
증상:
/health 접근 시 /api/auth/reissue 401

원인:
DB 초기화 후 브라우저에 남은 이전 Refresh Cookie와
공개 Route에서도 수행되던 Session Restore

해결:
/health에서는 Session Restore를 생략
Router Guard 회귀 테스트 추가
```

## 7. 최종 판정

```text
정적 구현: 통과
Backend 회귀 테스트: 통과
Frontend Test·TypeScript·Build: 통과
Docker Image Build: 통과
Docker Compose Runtime: 통과
MySQL·Backend·Frontend Healthy: 통과
REST·MySQL·WebSocket Health: 통과
인증 회귀: 통과
2계정 Phase 5 게임 회귀: 통과
F5 상태 복구: 통과
상대 Rack Privacy: 통과
실제 Secret·금지 파일 제외: 통과

Phase 5.5-A: FINAL
```

## 8. 다음 단계

```text
Phase 5.5-B — Minimum Local Kubernetes Runtime
```

현재 Docker Image를 재사용해 다음을 구성한다.

```text
MySQL StatefulSet + PVC
Backend Deployment + Service
Frontend Deployment + Service
ConfigMap
Secret
Readiness Probe
Liveness Probe
```

현재 In-memory Replay Store와 Spring Simple Broker 구조 때문에 Backend는 다음 단계에서도 단일 Replica를 유지한다.

```text
replicas: 1
```
