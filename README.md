# Realtime Tile Game — Phase 7 Minimum Turn Commit Loop

2~4인이 브라우저에서 함께 플레이하는 실시간 숫자 타일 게임이다. Spring Boot·Vue·MySQL·STOMP WebSocket을 사용하며, 확정된 게임 상태는 MySQL을 원본으로 삼는다.

## 현재 완료 범위

### Phase 0~5 FINAL

- REST·MySQL·WebSocket Health
- 회원가입·로그인·Refresh Token·프로필·로그아웃
- 2~4인 CLASSIC 로비·대기방·READY·START
- GameTile 106개 영속화와 참가자별 Rack 14개 초기 분배
- 공개 상태와 사용자별 Private Rack 분리
- 현재 턴 Draw, Pool Empty PASS, gameVersion·actionId Replay
- F5·재로그인 Active Game 복구

### Phase 5.5-A FINAL

- MySQL·Backend·Frontend Nginx Docker Compose 통합 실행
- Backend Java 17 Multi-stage Build와 Non-root Runtime
- Frontend Node 22 Multi-stage Build와 Nginx 정적 배포
- SPA Fallback, `/api/**` REST Proxy, `/ws` WebSocket Proxy
- Same-origin Endpoint Resolver와 세 서비스 Health Check
- MySQL Named Volume 유지

### Phase 5.5-B FINAL — Minimum Local Kubernetes Runtime

- Docker Desktop 내장 Kubernetes용 Namespace와 선언형 Manifest
- MySQL StatefulSet 1개, Headless/ClusterIP Service, PVC 2Gi
- Backend Deployment 1개, Recreate 전략, ClusterIP Service
- Frontend Deployment 1개, NodePort `30517`
- ConfigMap과 Cluster 직접 생성 Secret 분리
- Backend MySQL 대기 Init Container
- Startup·Readiness·Liveness Probe
- Resource requests/limits와 Service Account Token 비활성화
- Local Image `imagePullPolicy: Never`
- Docker Desktop kind 3 Node Local Image 적재 검증
- REST·MySQL·WebSocket/STOMP·2계정 게임 회귀 통과
- Backend·Frontend Self-healing 통과
- MySQL Pod 재생성 후 PVC·PV·User 데이터 유지 통과

### Phase 5.5-C FINAL — Minimum Classroom LAN Deployment Readiness

- Windows Host의 정확한 사설 LAN IPv4 선택
- `127.0.0.1`과 선택 LAN IPv4에만 관리형 `kubectl port-forward` 바인딩
- localhost·127.0.0.1·선택 LAN Origin으로 Backend ConfigMap 갱신
- Backend Recreate Rollout 완료 확인
- PID·시작 시각·Command Line 검증을 통한 중복 실행·PID 재사용 방어
- Windows Firewall Inbound TCP `30517`, `LocalSubnet` 전용 규칙 관리
- Start·Stop·Status·Test·Self-test 스크립트
- Host와 두 번째 Client PC 검증·장애 진단 문서

실제 Windows Host와 내부망 Client PC Runtime 검증, 보완 Self-test 11/11, 관리자 Start·Status·Test, localhost/LAN Health, Database, Stop 이후 Workload·PVC 유지까지 확인해 Phase 5.5-C를 FINAL PASS로 확정했다.

### Phase 6 FINAL — Rack UI, Sort and Motion Polish

- Rummikub-inspired 게임 보드와 789/777/원래 순서
- 서버 Rack과 표시 순서 분리
- 상대 턴 Rack 정렬
- fixed slot geometry, RAF throttle, dead zone, drag ghost/placeholder
- Draw 진입 및 Rack 이동 애니메이션

### Phase 7 FINAL Candidate — Minimum TurnDraft, Unified Working Table and Closeout

- 789/777 시각 그룹의 Hold Group Drag와 복수 타일 Overlay
- 로컬 TurnDraft, 편집·Undo·Cancel, Rack partition 보존
- 같은 턴 여러 Meld 첫 등록 합산 30점
- 기존 `TurnCommitValidator`를 사용한 서버 권위 검증
- V5 `game_melds`와 TABLE 타일 영속화
- typed `tableMelds`, COMMIT transaction, action replay, version 충돌 방어
- Rack 감소, `initialMeldCompleted`, `gameVersion`, 다음 턴 원자적 갱신
- 모든 참가자 공개 Table과 사용자별 Private Rack 동기화

대표 수용 시나리오 RED 789=24 + BLUE 123=6을 실제 2계정 브라우저에서 COMMIT하고 새로고침·다음 계정 동기화까지 확인했다.

## 프로젝트 구조

```text
.
├─ backend/                 Spring Boot 3.5 / Java 17 / JPA / Flyway
│  ├─ Dockerfile
│  └─ .dockerignore
├─ frontend/                Vue 3 / TypeScript / Vite / Pinia / Vitest
│  ├─ Dockerfile
│  ├─ .dockerignore
│  └─ nginx/default.conf
├─ k8s/                     Docker Desktop Kubernetes Manifest와 운영 안내
├─ scripts/classroom-lan/   학원 내부망 Start·Stop·Status·Test·Firewall 도구
├─ docs/                    Phase별 결정·검증·운영 문서
├─ compose.yaml             MySQL + Backend + Frontend
├─ .env.example             비밀값 없는 Compose 환경변수 예시
└─ README.md
```

# 실행 방식 1 — PowerShell 직접 실행

## MySQL

```powershell
docker compose up -d mysql
```

## Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

## Frontend

개인 파일 `frontend/.env.local`에 다음 값을 둘 수 있다.

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

```powershell
cd frontend
npm ci
npm run dev
```

# 실행 방식 2 — Docker Compose

## 1. 환경 파일 생성

```powershell
Copy-Item .env.example .env
```

`.env`에서 최소 다음 값을 실제 로컬 값으로 바꾼다.

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
JWT_ACCESS_SECRET_BASE64
```

JWT Secret 생성 예:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

`.env`는 저장소·Patch·문서 ZIP에 포함하지 않는다.

## 2. 전체 서비스 기동

```powershell
docker compose up -d --build
docker compose ps
```

기본 접속:

```text
Frontend  http://127.0.0.1:5173
Backend   http://127.0.0.1:8080
MySQL     127.0.0.1:33307
Health    http://127.0.0.1:5173/health
```

`33307`은 MySQL host port이며 브라우저 접속 포트가 아니다. `localhost`도 일반적으로 동일하지만, 별도 IPv6 개발 서버가 실행 중인 환경에서는 Compose가 명시적으로 바인딩한 `127.0.0.1:5173`을 사용한다.

브라우저 통신:

```text
http://localhost:5173/api/** → Nginx → backend:8080/api/**
ws://localhost:5173/ws      → Nginx → backend:8080/ws
```

## 3. 종료

```powershell
docker compose down
```

다음 명령은 Compose DB Volume을 삭제한다.

```powershell
docker compose down -v
```

# 실행 방식 3 — Docker Desktop Kubernetes

공식 환경:

```text
Windows
Docker Desktop
Docker Desktop Kubernetes
kubectl context docker-desktop
단일 Node 또는 Docker Desktop kind 다중 Node
```

## 1. Cluster 확인

```powershell
kubectl config current-context
kubectl cluster-info
kubectl get nodes
kubectl get storageclass
```

기대:

```text
Context: docker-desktop
Node: Ready
Default StorageClass 존재
```

## 2. Local Image 빌드

```powershell
docker build -t realtime-tile-game-backend:local ./backend
docker build -t realtime-tile-game-frontend:local ./frontend

docker image inspect realtime-tile-game-backend:local
docker image inspect realtime-tile-game-frontend:local
```

Manifest가 `imagePullPolicy: Never`를 사용하므로 Docker Desktop Kubernetes가 볼 수 있는 Local Image가 반드시 있어야 한다.

`kubectl get nodes`가 `desktop-control-plane`, `desktop-worker`, `desktop-worker2`처럼 다중 Node라면 각 kind Node에 Image를 적재한다.

```powershell
kind load docker-image `
  realtime-tile-game-backend:local `
  realtime-tile-game-frontend:local `
  --name desktop
```

`kind create cluster`와 `kind delete cluster`는 실행하지 않는다. Docker Desktop이 관리하는 기존 `desktop` Cluster를 사용한다.

## 3. Compose 중지

```powershell
docker compose down
```

Compose Named Volume은 삭제하지 않는다. Compose Volume과 Kubernetes PVC는 서로 다른 저장소이며 이번 Phase에서 데이터를 Migration하지 않는다.

## 4. Manifest 검증과 Namespace 생성

```powershell
kubectl apply --dry-run=client -f k8s/
kubectl apply -f k8s/00-namespace.yaml
```

## 5. Secret을 Cluster에 직접 생성

실제 Secret YAML은 저장소에 만들지 않는다.

```powershell
$mysqlPassword = Read-Host "MySQL user password"
$mysqlRootPassword = Read-Host "MySQL root password"
$jwtSecret = Read-Host "JWT Base64 secret"

kubectl -n realtime-tile-game create secret generic realtime-tile-game-secret `
  --from-literal=MYSQL_PASSWORD="$mysqlPassword" `
  --from-literal=MYSQL_ROOT_PASSWORD="$mysqlRootPassword" `
  --from-literal=JWT_ACCESS_SECRET_BASE64="$jwtSecret" `
  --dry-run=client `
  -o yaml |
kubectl apply -f -
```

Secret 값은 화면이나 문서에 출력하지 않는다. Base64는 암호화가 아니다.

## 6. 전체 Apply와 Rollout 확인

```powershell
kubectl apply -f k8s/

kubectl rollout status statefulset/mysql -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s

kubectl get all -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
```

기대 상태:

```text
mysql-0       Running / Ready 1/1
backend-*     Running / Ready 1/1
frontend-*    Running / Ready 1/1
MySQL PVC     Bound
```

브라우저 접속:

```text
http://localhost:30517
http://localhost:30517/health
```

Docker Desktop kind 다중 Node에서 NodePort가 Windows `localhost`로 직접 연결되지 않으면 다음 터미널을 계속 열어둔다.

```powershell
kubectl port-forward service/frontend 30517:80 -n realtime-tile-game
```

다른 터미널에서 확인한다.

```powershell
curl -i http://localhost:30517/api/health
```

브라우저 통신:

```text
http://localhost:30517/api/** → Frontend NodePort → Nginx → backend:8080
ws://localhost:30517/ws      → Frontend NodePort → Nginx → backend:8080
```

Backend와 MySQL은 ClusterIP만 사용하며 Host에 NodePort로 공개하지 않는다.

## Kubernetes 운영 명령

```powershell
kubectl get pods -n realtime-tile-game -o wide
kubectl get endpoints -n realtime-tile-game
kubectl get events -n realtime-tile-game --sort-by=.lastTimestamp
kubectl logs -n realtime-tile-game deployment/backend
kubectl logs -n realtime-tile-game deployment/frontend
kubectl logs -n realtime-tile-game statefulset/mysql
kubectl exec -n realtime-tile-game deployment/frontend -- wget -qO- http://backend:8080/api/health
```

같은 `:local` Tag를 다시 빌드한 경우:

```powershell
kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout restart deployment/frontend -n realtime-tile-game
```

ConfigMap이나 Secret 변경 후 Environment를 반영하려면 관련 Workload를 재시작해야 한다.

MySQL Final Probe는 Unix Socket을 사용하고 Probe 실행 명령에서만 `MYSQL_PWD`를 지정한다. MySQL Container 전역 `MYSQL_PWD`는 공식 Entrypoint 최초 초기화 흐름에 간섭할 수 있으므로 사용하지 않는다.

기존 PVC가 있는 상태에서 Secret의 MySQL 비밀번호만 바꾸면 DB 내부 계정 비밀번호는 자동으로 바뀌지 않는다. 운영 데이터가 있는 PVC를 비밀번호 재설정 목적으로 삭제하지 않는다.

## Kubernetes 데이터 유지 경고

MySQL Pod만 삭제하면 기존 PVC를 재사용해야 한다.

```powershell
kubectl delete pod mysql-0 -n realtime-tile-game
```

다음 명령은 Kubernetes DB 데이터를 삭제한다. 일반 종료 방식으로 사용하지 않는다.

```powershell
kubectl delete pvc -n realtime-tile-game -l app.kubernetes.io/component=mysql
```

Docker Desktop의 Reset Kubernetes Cluster도 일반 종료 방식이 아니다.


# 실행 방식 4 — Classroom LAN Client 연결

전제:

```text
Phase 5.5-B Kubernetes Runtime Ready
kubectl context docker-desktop
Windows Host와 Client PC가 같은 신뢰 가능한 학원 내부망
```

## 1. 운영 로직 Self-test

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

기대 결과:

```text
Classroom LAN self-test result: 11 passed, 0 failed
```

11번째 Case는 Windows Firewall 조회의 `PermissionDenied / Windows System Error 5`가 `NotFound`가 아니라 `AccessDenied`로 분류되고, Status가 존재 여부를 빈 값으로 유지하는지 검증한다.

## 2. Firewall Rule 생성

관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1 -Port 30517
```

프로젝트 규칙은 Inbound TCP `30517`, `RemoteAddress LocalSubnet`만 허용한다. Windows Firewall 전체를 비활성화하지 않는다.

## 3. LAN Bridge 시작

Firewall 조회까지 포함하는 일반 권장 실행은 **관리자 PowerShell**이다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

일반 권한 PowerShell에서 Windows가 Firewall Rule 조회를 차단하면 Start는 Rule 누락으로 오인하지 않고 다음 의미로 종료한다.

```text
Firewall verification requires an elevated PowerShell.
Run Start-ClassroomLan.ps1 as administrator.
```

`-SkipFirewallCheck`는 Firewall 검사를 의도적으로 생략하는 로컬 진단에서만 사용한다.

실제 Host 주소로 교체한다. `-LanIp`을 생략하면 안전 후보가 정확히 하나일 때만 자동 선택한다.

Start는 Source Manifest를 수정하지 않는다. Cluster ConfigMap의 `CORS_ALLOWED_ORIGINS`를 다음 정확한 세 값으로 교체하고 Backend Rollout을 기다린다.

```text
http://localhost:30517
http://127.0.0.1:30517
http://<선택한_HOST_LAN_IPV4>:30517
```

Port-forward는 `0.0.0.0`이 아니라 `127.0.0.1`과 선택한 LAN IPv4에만 바인딩한다.

## 4. 상태와 Host 검증

빠른 운영 재검증은 관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

일반 권한 Status에서 Firewall 조회가 차단되면 존재 여부를 `False`로 단정하지 않는다.

```text
FirewallRuleExists :
FirewallRuleValid  : False
FirewallReason     : AccessDenied
```

`AccessDenied`는 Rule 누락을 뜻하는 `NotFound`와 다른 장애다.

## 5. Client PC 검증

```powershell
Test-NetConnection 192.168.0.10 -Port 30517
Invoke-RestMethod http://192.168.0.10:30517/api/health
```

브라우저:

```text
http://192.168.0.10:30517
http://192.168.0.10:30517/health
```

Host와 Client가 다른 VLAN이거나 학원 AP에 Client Isolation이 설정돼 있으면 애플리케이션이 정상이어도 접속이 차단될 수 있다.

## 6. 중지

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

이 명령은 관리되는 Port-forward만 종료한다. Kubernetes Workload와 PVC는 유지한다.

Firewall 규칙도 제거할 때만 관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Disable-ClassroomLanFirewall.ps1 -Port 30517
```

Runtime PID·Log는 `.runtime/classroom-lan/`에 저장되며 Git·Patch·클린 ZIP에서 제외된다.

## 실제 Host/Client Runtime 검증 기록

다음 항목은 실제 Windows Host와 같은 내부망의 Client PC에서 확인됐다.

```text
PowerShell Self-test 10/10(권한 분류 Case 추가 전 기준)
Firewall Rule 생성·중복 실행 안전
Classroom LAN Start·CORS 갱신·Backend Rollout
127.0.0.1 + 선택 LAN IPv4 Bind
Status·Test·localhost/LAN Health·Database UP
중복 Start PID 유지
Stop 시 관리 Port-forward만 종료
Stop 후 LAN URL 연결 실패
재시작 시 새 PID로 정상 기동
Kubernetes Workload/PVC 유지
Client PC 접속
Client PC 방 생성·게임 시작
로그아웃 후 재접속
```

Runtime 보완 후 Self-test는 11개이며 관리자 PowerShell에서 Start·Status·Test를 빠르게 재검증한다. 전체 Client 게임 회귀는 Firewall 오류 분류만 변경됐으므로 반복 대상이 아니다.

# 품질 검증

Backend:

```powershell
cd backend
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd frontend
npm ci
npm run check
```

Docker Image:

```powershell
docker build -t realtime-tile-game-backend:local ./backend
docker build -t realtime-tile-game-frontend:local ./frontend
```

Kubernetes:

```powershell
kubectl apply --dry-run=client -f k8s/
kubectl get all -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
```

# 인프라 트러블슈팅

Phase 5.5-A Docker Compose와 Phase 5.5-B Kubernetes 실검증 중 확인한 문제와 안전한 복구 절차는 다음 문서에 통합돼 있다.

```text
docs/Realtime_Tile_Game_Phase5_5A_5B_Troubleshooting_Guide.md
```

다음 항목을 포함한다.

```text
Shell 종류 혼동
Namespace 누락
kind ErrImageNeverPull·Image Load
MySQL ERROR 1130·Unix Socket Probe
MYSQL_PWD 범위
Secret/PVC Password 불일치
PVC Terminating
Backend Init:0/1
NodePort·Port-forward
Self-healing·PVC 데이터 유지
안전한 중지·재시작
```

# Architecture 제약

- Backend는 `replicas: 1`, `strategy: Recreate` 고정
- In-memory Replay Store와 Spring Simple Broker는 단일 Backend Process 기준
- MySQL은 StatefulSet 1개와 PVC를 확정 상태 원본으로 사용
- Frontend만 NodePort `30517`로 외부 노출
- Backend·MySQL NodePort, Ingress, Helm, Redis, External STOMP Broker 미사용
- 실제 Secret은 Manifest·Git·Patch·클린 ZIP에 포함하지 않음
- Compose Volume과 Kubernetes PVC는 별도이며 자동 Migration하지 않음

# 다음 단계

```text
Phase 7 FINAL Candidate 자동검증
→ 2·3계정 Browser Runtime
→ Console error/warning 0
→ Phase 7 FINAL 확정
```

# Phase 7 Second Review — Unified Working Table

Phase 7의 Table 편집은 별도 대형 TurnDraft가 아니라 서버 `tableMelds`를 복제한 Local Working Table 한 곳에서 수행한다.

```text
첫 등록 전: 기존 Table 잠금 + 새 Meld만 추가
첫 등록 후: 기존 Meld 수정·분리·병합 + Rack Tile 직접 추가
Commit: 변경분이 아니라 전체 Candidate Table 전송
Server: 기존 Rule Engine 검증 후 한 트랜잭션으로 Table 치환
```

21~30장 Rack은 2행 Adaptive Column/Tile Size를 사용하며 내 턴 Rack/Action에는 녹색 강조와 badge가 표시된다. 구현·검증 상세는 `docs/Phase7_Second_Review_Completion_Report.md`를 참조한다.


# Phase 7 Production Closeout — FINAL Candidate

Renderer 탐색은 종료했다. Production은 기존 HTML5 Drag + DOM을 유지하며 Native Pointer와 Konva는 적용하지 않는다.

```text
Committed Table: Full Flow
Working Table: Local Reflow
Meld 사이 최소 1 Cell Gutter
충돌 시 오른쪽으로 Nudge 후 다음 행 Wrap
논리 Table 18행 / 화면 Viewport 8행
내부 세로 Scroll + Bottom Drop Row + Edge Auto-scroll
Undo History 최대 100
첫 등록 완료 후 0/30 문구 제거
```

자동검증과 실제 배포 검증 기준은 다음 문서를 사용한다.

```text
docs/Phase7_FINAL_Production_Closeout_Implementation_Summary.md
docs/Phase7_FINAL_Production_Closeout_Verification_Result.md
docs/Phase7_FINAL_Production_Closeout_Runtime_Verification_Guide.md
scripts/phase7/Verify-Phase7FinalCandidate.ps1
```

현재 상태는 `FINAL Candidate`다. Backend 전체 Maven 테스트와 실제 2·3계정 Browser Runtime을 사용자의 Windows/Docker Desktop 환경에서 통과한 뒤 `Phase 7 FINAL`로 확정한다.
