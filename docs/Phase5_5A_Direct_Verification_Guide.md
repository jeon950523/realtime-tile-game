# Phase 5.5-A Direct Verification Guide

작성 기준: 2026-07-16 KST

기준 전체본:

```text
phase0716-00-31-phase5-final-clean-source-fixed.zip
```

## 1. 사전 정리

PowerShell 직접 실행 중인 Backend·Frontend가 있으면 종료한다.

```powershell
docker compose down
```

기존 MySQL Named Volume은 삭제하지 않는다.

금지:

```powershell
docker compose down -v
```

## 2. 환경변수

```powershell
Copy-Item .env.example .env
```

`.env`에서 실제 로컬 값 입력:

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
JWT_ACCESS_SECRET_BASE64
```

JWT Secret 생성:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

표준 Base64이며 Decode 결과가 최소 32바이트여야 한다.

## 3. Host 회귀 테스트

Backend:

```powershell
cd backend
.\mvnw.cmd clean test
cd ..
```

기대:

```text
Tests run: 278
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

실제 출력이 최종 기준이다.

Frontend:

```powershell
cd frontend
npm ci
npm run check
cd ..
```

기대:

```text
Test Files 14 passed
Tests 97 passed
TypeScript 통과
Production Build 통과
```

## 4. Docker Image Build

최초 검증:

```powershell
docker compose build --no-cache
```

캐시 Build 회귀:

```powershell
docker compose build
```

이미지 확인:

```powershell
docker compose images
docker image inspect realtime-tile-game-backend:local
docker image inspect realtime-tile-game-frontend:local
```

확인:

```text
Backend Image Env에 JWT·DB Password 없음
Frontend Image Env에 Secret 없음
Backend Runtime에 src 전체 없음
Frontend Runtime에 node_modules·npm Cache 없음
```

## 5. 전체 기동

```powershell
docker compose up -d
docker compose ps
```

기대:

```text
mysql healthy
backend healthy
frontend healthy
```

문제 발생 시:

```powershell
docker compose logs --tail=200 mysql
docker compose logs --tail=200 backend
docker compose logs --tail=200 frontend
```

## 6. Health와 Same-origin

브라우저:

```text
http://localhost:5173/health
```

확인:

```text
Backend REST 정상
MySQL 정상
WebSocket/STOMP 정상
```

Network 탭:

```text
/api/health Host = localhost:5173
/ws Host = localhost:5173
```

다음 직접 호출이 애플리케이션 통신으로 나타나면 실패다.

```text
http://localhost:8080/api/**
ws://localhost:8080/ws
```

## 7. SPA F5

다음 페이지에서 F5:

```text
/login
/lobby
/health
```

Nginx 404 없이 Vue 화면이 복구돼야 한다.

## 8. 인증

```text
회원가입
로그인
F5
Refresh 인증 복구
프로필
로그아웃
```

Cookie:

```text
rtg_refresh
HttpOnly
```

Nginx Proxy 뒤에서도 저장·전송돼야 한다.

## 9. 2계정 Phase 3~5 회귀

Chrome 일반 창 A와 시크릿 창 B:

```text
2계정 로그인
방 생성
입장
READY
START
Rack 14·14
Pool 78
현재 턴 Draw
Pool 77
Draw 사용자 Rack 15
다음 턴 전환
```

Privacy:

```text
자기 Rack 상세만 표시
상대 Rack Count만 표시
```

## 10. F5 복구

양쪽 F5 후:

```text
같은 gameId
최신 Pool
최신 Rack Count
최신 현재 턴
자기 Rack 상세 복구
WebSocket CONNECTED
```

## 11. Backend 재시작

게임 진행 중:

```powershell
docker compose restart backend
docker compose ps
```

Backend가 다시 Healthy가 된 뒤:

```text
Frontend WebSocket 재연결
Game REST Snapshot 복구
DB Game State 유지
```

## 12. 전체 Down/Up과 Volume

```powershell
docker compose down
docker compose up -d
docker compose ps
```

확인:

```text
회원·Room·Game·GameTile 유지
Flyway V1~V4 유지
Active Game 복구
```

일반 종료:

```powershell
docker compose down
```

DB 데이터를 완전히 삭제할 때만:

```powershell
docker compose down -v
```

## 13. Console과 Log

```text
Browser Console Error 0
Nginx 4xx/5xx 반복 없음
Backend Unexpected Exception 없음
MySQL Crash Loop 없음
```

## 14. 최종 기록

```text
Backend Tests run:
Frontend Tests run:
TypeScript:
Production Build:
Docker No-cache Build:
Docker Cached Build:
mysql healthy:
backend healthy:
frontend healthy:
REST Same-origin:
MySQL:
WebSocket Same-origin:
SPA F5:
Refresh Cookie:
2계정 Game:
F5 Game Recovery:
Backend Restart:
Down/Up Volume:
Image Secret Audit:
Console Error:
```
