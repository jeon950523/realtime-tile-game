# Realtime Tile Game Phase 5.5-A·5.5-B 트러블슈팅 가이드

작성 기준: 2026-07-16 KST

대상 환경:

```text
Windows
Docker Desktop
Docker Desktop Kubernetes
kubectl context docker-desktop
Docker Desktop kind 기반 3 Node
```

대상 범위:

```text
Phase 5.5-A — Minimum Dockerized Local Runtime
Phase 5.5-B — Minimum Local Kubernetes Runtime
```

이 문서는 Phase 5.5-A·5.5-B 실검증에서 실제로 발생했거나 직접 확인한 문제를 증상, 원인, 확인 명령, 안전한 조치 순서로 정리한다.

---

## 1. 공통 진단 원칙

문제가 발생하면 다음 순서를 유지한다.

```text
1. 현재 Shell 확인
2. kubectl Context와 Namespace 확인
3. Local Image 존재와 kind Node 적재 여부 확인
4. Pod·PVC·Event 확인
5. MySQL Probe·Secret·PVC 상태 확인
6. Backend Init Container와 Application Log 확인
7. Frontend Service와 Port-forward 확인
8. 데이터 삭제 명령은 마지막에만 검토
```

다음 명령을 먼저 실행한다.

```powershell
kubectl config current-context
kubectl get nodes
kubectl get all -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
kubectl get events -n realtime-tile-game --sort-by=.lastTimestamp
```

금지:

```text
원인 확인 전 Namespace 전체 삭제
원인 확인 전 PVC 삭제
docker compose down -v를 일반 재시작에 사용
Docker Desktop Kubernetes Reset을 일반 복구 절차로 사용
Secret 값을 화면이나 문서에 출력
```

---

## 2. Shell 종류 혼동

### 증상

```text
'\'가 명령으로 실행됨
PowerShell의 '`'가 Git Bash에서 동작하지 않음
환경변수 문법이 그대로 출력됨
여러 줄 kubectl 명령이 중간에서 끊김
```

### 원인

PowerShell, CMD, Git Bash는 줄 연결과 변수 문법이 다르다.

```text
PowerShell 줄 연결: `
Git Bash 줄 연결: \
PowerShell 변수: $value
Git Bash 변수: $VALUE
```

### 확인

PowerShell:

```powershell
$PSVersionTable.PSVersion
```

Git Bash:

```bash
echo "$SHELL"
```

### 조치

문서의 코드 블록 언어를 확인하고 같은 Shell에서 실행한다. 혼동되면 여러 줄 명령을 한 줄로 합쳐 실행한다.

Secret 생성 예시는 Shell별 문법을 섞지 않는다.

PowerShell:

```powershell
$mysqlPassword = Read-Host "MySQL application user password"
$mysqlRootPassword = Read-Host "MySQL root password"
$jwtSecret = Read-Host "JWT Base64 secret"
```

Git Bash:

```bash
read -rsp "MySQL application user password: " MYSQL_PASSWORD; echo
read -rsp "MySQL root password: " MYSQL_ROOT_PASSWORD; echo
read -rsp "JWT Base64 secret: " JWT_ACCESS_SECRET_BASE64; echo
```

---

## 3. Namespace 누락

### 증상

```text
namespaces "realtime-tile-game" not found
secret 생성 실패
kubectl apply 일부 Resource 실패
```

### 원인

Namespace Resource를 적용하기 전에 Namespace 대상 명령을 실행했다.

### 확인

```powershell
kubectl get namespace realtime-tile-game
```

### 조치

```powershell
kubectl apply -f k8s/00-namespace.yaml
kubectl get namespace realtime-tile-game
```

그다음 Secret을 생성하고 전체 Manifest를 적용한다.

```powershell
kubectl apply -f k8s/
```

`default` Namespace에 우회 배포하지 않는다.

---

## 4. kind 환경의 ErrImageNeverPull

### 증상

```text
ErrImageNeverPull
ImagePullBackOff
Container image "realtime-tile-game-*:local" is not present with pull policy of Never
```

### 원인

Manifest는 다음 정책을 사용한다.

```yaml
imagePullPolicy: Never
```

Docker Desktop의 실제 Kubernetes가 kind 기반 다중 Node이면 Host Docker에 Image가 있어도 해당 kind Node Container에는 Image가 없을 수 있다.

### 확인

```powershell
docker image inspect realtime-tile-game-backend:local
docker image inspect realtime-tile-game-frontend:local
kubectl get nodes
kubectl get pods -n realtime-tile-game -o wide
kubectl describe pod <POD_NAME> -n realtime-tile-game
```

### 조치

먼저 Local Image를 만든다.

```powershell
docker build -t realtime-tile-game-backend:local ./backend
docker build -t realtime-tile-game-frontend:local ./frontend
```

Docker Desktop kind Cluster의 각 Node가 Image를 볼 수 있도록 적재한다.

```powershell
kind load docker-image `
  realtime-tile-game-backend:local `
  realtime-tile-game-frontend:local `
  --name desktop
```

실검증 기준 Cluster 이름은 `desktop`이었다. 환경에 따라 Docker Desktop이 관리하는 kind Cluster 이름이나 Node 구성이 다르면 먼저 다음을 확인한다.

```powershell
kind get clusters
kubectl get nodes
```

Image를 적재한 뒤 Workload를 재시작한다.

```powershell
kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout restart deployment/frontend -n realtime-tile-game
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s
```

금지:

```text
문제를 숨기기 위해 imagePullPolicy를 Always로 변경
검증 없이 Registry Push 방식으로 전환
Backend Replica를 늘려 우회
```

---

## 5. MySQL ERROR 1130

### 증상

```text
ERROR 1130 (HY000): Host '127.0.0.1' is not allowed to connect to this MySQL server
MySQL Readiness Probe 실패
mysql-0 Running이지만 Ready 0/1
```

### 원인

MySQL Probe가 `127.0.0.1` TCP 연결을 사용하면서 Host 기반 계정 허용 정책과 충돌했다.

### 확정 조치

최종 Manifest의 MySQL Probe는 TCP Loopback이 아니라 Unix Socket을 사용한다.

```text
mysqladmin ping --protocol=socket
mysql --protocol=socket ... SELECT 1
```

확인:

```powershell
kubectl get statefulset mysql -n realtime-tile-game -o yaml
```

Probe에 다음 조건이 있어야 한다.

```text
--protocol=socket
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
```

`127.0.0.1` TCP Probe로 되돌리지 않는다.

---

## 6. Unix Socket Probe와 MYSQL_PWD 범위

### 증상

```text
MySQL 최초 초기화 중 인증 오류
mysql-0 CrashLoopBackOff
초기화 로그에서 Client가 비밀번호를 너무 일찍 사용
```

### 원인

Container 전역 환경변수로 `MYSQL_PWD`를 지정하면 MySQL 공식 Entrypoint의 최초 초기화 과정에서 실행되는 Client 명령까지 영향을 받을 수 있다.

### 확정 구조

Container 전역 환경에는 다음만 유지한다.

```text
MYSQL_DATABASE
MYSQL_USER
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
TZ
```

`MYSQL_PWD`는 저장하지 않고 Probe 명령 내부에서만 임시 지정한다.

```sh
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysqladmin ping --protocol=socket -uroot --silent
```

금지:

```text
StatefulSet Container env에 MYSQL_PWD 추가
Secret 값을 Probe Command Argument에 Literal로 작성
TCP Loopback Probe 재도입
```

---

## 7. Secret과 기존 PVC의 Password 불일치

### 증상

```text
Access denied for user
Backend Init Container가 계속 대기
Backend가 DB 인증 실패로 시작하지 못함
새 Secret을 적용했는데 기존 MySQL 계정 Password는 바뀌지 않음
```

### 원인

MySQL Image의 초기화 환경변수는 빈 데이터 디렉터리의 최초 초기화에서만 DB 계정을 만든다. 기존 PVC를 유지한 채 Kubernetes Secret만 변경해도 PVC 내부 MySQL 계정 Password는 자동 변경되지 않는다.

### 확인

```powershell
kubectl get pvc -n realtime-tile-game
kubectl logs mysql-0 -n realtime-tile-game
kubectl logs deployment/backend -n realtime-tile-game
kubectl get secret realtime-tile-game-secret -n realtime-tile-game
```

Secret 값을 출력하지 않는다.

### 안전한 조치

보존할 데이터가 있으면 PVC를 삭제하지 않는다. 기존 DB에 정상 인증 가능한 계정으로 접속해 MySQL 계정 Password를 변경한 뒤 Secret과 일치시킨다.

아직 초기 인프라 검증 단계이고 보존할 데이터가 없으며 초기화를 명시적으로 승인한 경우에만 해당 프로젝트 PVC를 삭제하고 현재 Secret으로 새로 초기화한다.

```powershell
kubectl scale deployment/backend --replicas=0 -n realtime-tile-game
kubectl scale statefulset/mysql --replicas=0 -n realtime-tile-game
kubectl delete pvc mysql-data-mysql-0 -n realtime-tile-game
kubectl scale statefulset/mysql --replicas=1 -n realtime-tile-game
kubectl rollout status statefulset/mysql -n realtime-tile-game --timeout=180s
kubectl scale deployment/backend --replicas=1 -n realtime-tile-game
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
```

이 절차는 DB 데이터를 삭제한다. 일반 재시작 절차로 사용하지 않는다.

---

## 8. PVC Terminating

### 증상

```text
PVC가 Terminating에서 멈춤
새 PVC가 생성되지 않음
MySQL StatefulSet가 Pending 또는 ContainerCreating에서 진행되지 않음
```

### 원인 후보

```text
MySQL Pod가 PVC를 계속 사용 중
StatefulSet가 Pod를 즉시 재생성
PVC 보호 Finalizer가 사용 중인 Volume 삭제를 막음
```

### 확인

```powershell
kubectl get pod,pvc,pv -n realtime-tile-game
kubectl describe pvc mysql-data-mysql-0 -n realtime-tile-game
kubectl get events -n realtime-tile-game --sort-by=.lastTimestamp
```

### 안전한 조치

PVC를 정말 삭제해야 하는 승인된 초기화 상황에서만 먼저 사용 Workload를 중지한다.

```powershell
kubectl scale deployment/backend --replicas=0 -n realtime-tile-game
kubectl scale statefulset/mysql --replicas=0 -n realtime-tile-game
kubectl get pods -n realtime-tile-game
kubectl delete pvc mysql-data-mysql-0 -n realtime-tile-game
```

Finalizer를 강제로 제거하기 전에 Pod가 Volume을 사용하지 않는지와 보존 데이터 삭제 승인을 다시 확인한다.

금지:

```text
일반 장애 복구 중 PVC Finalizer 강제 제거
Namespace 전체 삭제로 우회
데이터 백업 없이 Docker Desktop Cluster Reset
```

---

## 9. Backend Init:0/1

### 증상

```text
backend-*   Init:0/1
Backend Deployment Available 0/1
Frontend /api 응답 502 또는 연결 실패
```

### 원인

Backend Init Container는 MySQL이 인증 가능한 상태가 될 때까지 기다린다. 다음 중 하나면 계속 대기할 수 있다.

```text
mysql Service DNS 오류
mysql-0 Not Ready
Secret Password 불일치
MySQL 초기화 실패
PVC 상태 문제
```

### 확인 순서

```powershell
kubectl get pods,svc,pvc -n realtime-tile-game
kubectl describe pod -l app.kubernetes.io/component=backend -n realtime-tile-game
kubectl logs deployment/backend -n realtime-tile-game -c wait-for-mysql
kubectl logs mysql-0 -n realtime-tile-game
kubectl get endpoints mysql -n realtime-tile-game
```

### 조치

```text
1. mysql-0의 Startup·Readiness 상태 확인
2. mysql Service Endpoint 확인
3. Secret/PVC Password 수명주기 확인
4. MySQL Probe가 Unix Socket인지 확인
5. 원인을 수정한 뒤 Backend Rollout 재시작
```

```powershell
kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
```

Init Container를 제거하거나 DB 대기를 임의로 건너뛰지 않는다.

---

## 10. NodePort localhost 직접 접근 실패

### 증상

```text
Frontend Service는 NodePort 30517
Pod와 Service는 정상
http://localhost:30517 연결 실패
```

### 원인

실제 Docker Desktop kind 기반 다중 Node 환경에서 NodePort가 Windows Host의 `localhost`로 직접 매핑되지 않을 수 있다.

### 확인

```powershell
kubectl get service frontend -n realtime-tile-game
kubectl get pods -n realtime-tile-game -o wide
kubectl get nodes
Test-NetConnection 127.0.0.1 -Port 30517
```

### 로컬 검증 조치

Frontend Service를 Port-forward한다.

```powershell
kubectl port-forward service/frontend 30517:80 -n realtime-tile-game
```

다른 터미널에서:

```powershell
Invoke-RestMethod http://127.0.0.1:30517/api/health
```

Phase 5.5-C 학원 내부망 접속에서는 관리 스크립트가 다음 두 주소에만 바인딩한다.

```text
127.0.0.1
선택한 Host LAN IPv4
```

NodePort Manifest는 유지하며, Backend와 MySQL을 NodePort로 추가 노출하지 않는다.

---

## 11. Port-forward 재연결

### 증상

```text
기존 브라우저 연결이 갑자기 끊김
connection refused
Backend/Frontend Pod 재생성 후 Port-forward 종료
```

### 원인

`kubectl port-forward`는 연결 대상 Pod나 Service Backend 변화, 터미널 종료, kubectl Process 종료 시 끊길 수 있다.

### Phase 5.5-B 수동 복구

기존 Process가 끝났는지 확인한 뒤 다시 실행한다.

```powershell
kubectl port-forward service/frontend 30517:80 -n realtime-tile-game
```

### Phase 5.5-C 관리형 복구

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp <HOST_LAN_IPV4>
```

Start 스크립트는 저장 PID, Process 시작 시각, Command Line을 확인해 정상 Process는 중복 생성하지 않고 죽은 PID 상태만 정리한다.

---

## 12. Self-healing 검증

### Backend

```powershell
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=backend
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
```

기대:

```text
새 Backend Pod Ready 1/1
Backend 두 Pod 동시 Ready 없음
Frontend WebSocket 재연결
REST Snapshot으로 Game 상태 복구
```

### Frontend

```powershell
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=frontend
kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s
```

기대:

```text
새 Frontend Pod Ready 1/1
Frontend Service 유지
필요 시 Port-forward 재시작 후 화면 복구
```

### MySQL

```powershell
kubectl delete pod mysql-0 -n realtime-tile-game
kubectl rollout status statefulset/mysql -n realtime-tile-game --timeout=180s
```

기대:

```text
mysql-0 이름 유지
기존 PVC 재연결
Backend Readiness 복구
회원·Room·Game 데이터 유지
```

---

## 13. PVC 데이터 유지 확인

MySQL Pod 재생성 전:

```powershell
$pvcUidBefore = kubectl get pvc mysql-data-mysql-0 -n realtime-tile-game -o jsonpath='{.metadata.uid}'
$pvBefore = kubectl get pvc mysql-data-mysql-0 -n realtime-tile-game -o jsonpath='{.spec.volumeName}'
```

Pod 재생성:

```powershell
kubectl delete pod mysql-0 -n realtime-tile-game
kubectl rollout status statefulset/mysql -n realtime-tile-game --timeout=180s
```

재생성 후:

```powershell
$pvcUidAfter = kubectl get pvc mysql-data-mysql-0 -n realtime-tile-game -o jsonpath='{.metadata.uid}'
$pvAfter = kubectl get pvc mysql-data-mysql-0 -n realtime-tile-game -o jsonpath='{.spec.volumeName}'

$pvcUidBefore -eq $pvcUidAfter
$pvBefore -eq $pvAfter
```

애플리케이션에서도 기존 계정과 Active Game을 다시 조회한다. 단순히 Pod가 Ready라는 이유만으로 데이터 유지가 검증됐다고 판단하지 않는다.

---

## 14. 안전한 중지·재시작

### Docker Compose

일반 중지:

```powershell
docker compose down
```

재시작:

```powershell
docker compose up -d
```

데이터 삭제 명령:

```powershell
docker compose down -v
```

`-v`는 일반 중지에 사용하지 않는다.

### Kubernetes

Kubernetes Resource는 별도 종료 명령 없이 유지할 수 있다. 특정 Workload만 재시작한다.

```powershell
kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout restart deployment/frontend -n realtime-tile-game
kubectl rollout restart statefulset/mysql -n realtime-tile-game
```

상태 확인:

```powershell
kubectl get all -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
```

Phase 5.5-C 관리 Port-forward만 종료:

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

이 명령은 Namespace, Deployment, StatefulSet, PVC를 삭제하지 않는다.

### 데이터 삭제 경고

다음은 정상 중지 명령이 아니다.

```powershell
kubectl delete pvc mysql-data-mysql-0 -n realtime-tile-game
kubectl delete namespace realtime-tile-game
```

Compose Named Volume과 Kubernetes PVC는 서로 다른 저장소다. 한쪽을 중지하거나 삭제해도 다른 쪽 데이터로 자동 전환되지 않는다.

---

## 15. 빠른 장애 분류표

| 증상 | 우선 확인 | 대표 원인 | 안전한 첫 조치 |
|---|---|---|---|
| `ErrImageNeverPull` | Pod Event, 배치 Node | kind Node에 Local Image 없음 | `kind load docker-image` 후 Rollout |
| `mysql-0 0/1` | Probe, MySQL Log | TCP Probe·초기화·Password | Unix Socket Probe와 Secret/PVC 확인 |
| `Backend Init:0/1` | Init Log, mysql Endpoint | DB 준비/인증 실패 | MySQL부터 복구 후 Backend Rollout |
| `ERROR 1130` | MySQL Probe 명령 | 127.0.0.1 TCP Host 제한 | Unix Socket Probe 유지 |
| `Access denied` | Secret/PVC 수명주기 | 기존 DB Password와 Secret 불일치 | 데이터 보존 여부 확인 후 계정 Password 동기화 |
| PVC `Terminating` | Pod/PVC/PV, Event | Volume 사용 중 | Workload 중지 후 승인된 초기화만 진행 |
| `localhost:30517` 실패 | Service, Node 환경 | kind NodePort Host 미연결 | Frontend Service Port-forward |
| Port-forward 종료 | kubectl Process | Pod 재생성·터미널 종료 | 관리형 Start 또는 수동 재실행 |
| Pod 재생성 후 데이터 없음 | PVC UID/PV, Application 데이터 | 새 PVC 사용 또는 삭제 | 즉시 쓰기 중단 후 PVC/PV 연결 확인 |

---

## 16. 최종 확인 명령

```powershell
kubectl config current-context
kubectl get nodes
kubectl get all -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
kubectl get endpoints -n realtime-tile-game
kubectl get events -n realtime-tile-game --sort-by=.lastTimestamp
```

Frontend에서 Backend Service DNS 확인:

```powershell
kubectl exec -n realtime-tile-game deployment/frontend -- wget -qO- http://backend:8080/api/health
```

브라우저 통합 확인:

```text
/api/health
/ws
회원가입·로그인·Refresh Cookie
2계정 READY·START·DRAW
F5 Game 상태 복구
Console Error 0
```

원인이 Infrastructure인지 Application인지 분리한 뒤에만 코드를 변경한다.


---

## 17. Classroom LAN Firewall Rule이 있는데 NotFound로 표시됨

### 실제 증상

관리자 PowerShell에서 프로젝트 Rule 생성과 중복 실행은 정상인데 일반 권한 PowerShell의 조회에서 다음 오류가 발생할 수 있다.

```text
Get-NetFirewallRule
PermissionDenied
Windows System Error 5
```

오류를 `SilentlyContinue`로 숨기면 Rule이 실제로 존재해도 다음처럼 잘못 보일 수 있다.

```text
Exists False
Reason NotFound
```

### 원인

Windows 환경과 보안 정책에 따라 일반 권한 사용자가 Windows Firewall Rule을 조회하지 못한다. 이는 Rule 누락이 아니라 **조회 권한 부족**이다.

```text
NotFound
→ 조회는 성공
→ 해당 프로젝트 Rule이 없음

AccessDenied
→ 조회 자체가 실패
→ Rule 존재 여부를 판단할 수 없음
```

### 보완 후 상태 모델

```text
QuerySucceeded False
Exists null
IsValid False
Reason AccessDenied
```

Status 출력:

```text
FirewallRuleExists :
FirewallRuleValid  : False
FirewallReason     : AccessDenied
```

### 안전한 조치

관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp <HOST_LAN_IPV4>
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

Rule을 다시 만들기 전에 관리자 Status에서 다음을 확인한다.

```text
FirewallRuleExists True
FirewallRuleValid True
FirewallReason Match
```

`AccessDenied`를 `NotFound`로 보고 Rule을 반복 생성하거나 Windows Firewall을 비활성화하지 않는다. `-SkipFirewallCheck`는 의도적인 로컬 진단에서만 사용한다.

### Runtime 검증 기록

다음은 실제 Host/Client 환경에서 통과했다.

```text
Firewall Rule 생성·중복 실행
Classroom LAN Start
정확한 LAN Origin과 Backend Rollout
localhost/LAN Health와 Database
Status·Test
중복 PID 방지
Stop·재시작
Client PC 접속
방 생성·게임 시작
로그아웃 후 재접속
```

이번 보완 후에는 Self-test `11 passed, 0 failed`와 관리자 Start·Status·Test만 빠르게 재검증하면 된다.
