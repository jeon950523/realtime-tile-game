# 실시간 루미큐브형 타일 게임 — 새 채팅 인수인계 문서

## 0. 문서 기준

- 작성 시각: **2026-07-17 20:19 KST**
- 프로젝트: 실시간 루미큐브형 타일 게임
- 목적: 새 채팅에서 현재까지의 개발·검수·인프라 맥락을 잃지 않고 즉시 이어가기
- 현재 판정: **Phase 5.5-C — Classroom LAN Deployment Readiness FINAL PASS**
- 사용자 작업 경로(집): `E:\rumi\cube\phase`
- 사용자 작업 경로(학원에서 사용했던 경로): `E:\Jeon\cube\phase`
- 현재 Kubernetes Context: `docker-desktop`
- 현재 Docker Desktop Kubernetes 구성: `kind`, Kubernetes `v1.36.1`, Node `1개`
- 현재 Node 컨테이너 이름: `desktop-control-plane`

> 중요: 이 문서는 맥락 인수인계용이다. 새 채팅에서는 사용자가 업로드하는 최신 전체 소스 ZIP을 유일한 코드 기준으로 삼고, 문서에 적힌 과거 파일명만으로 코드를 추정하지 않는다.

---

# 1. 새 채팅 시작 시 바로 지켜야 할 운영 규칙

## 1.1 코드 기준

1. 사용자가 새 채팅에 업로드하는 **최신 전체 소스 ZIP**만 유일한 코드 기준으로 삼는다.
2. 과거 전체본·과거 Patch·과거 설명은 비교 참고만 하며 최신 코드보다 우선하지 않는다.
3. 코드 변경 결과는 원칙적으로 다음처럼 나눈다.
   - 코드 변경: 수정·생성 파일만 포함한 **Patch ZIP**
   - 문서 변경: 여러 문서를 하나의 **문서 전용 ZIP**
4. 전체 프로젝트 ZIP을 매번 다시 만들어 주지 않는다. 사용자는 전체 ZIP을 받으면 모든 파일을 다시 봐야 해서 불편해한다.
5. 사용자가 전체 최신본을 업로드하고 테스트 완료를 보고하면, 그 최신본 기준으로 문서·작업지시서·검수 프롬프트를 작성한다.

## 1.2 답변 방식

- 한국어 반말/평서체를 유지한다.
- 필수 수정과 선택 리팩터링을 구분한다.
- 구현 전에는 현재 코드·문서·테스트 기준을 먼저 확인한다.
- 사용자가 이미 통과시킨 Runtime 결과를 이유 없이 다시 전부 시키지 않는다.
- 작은 기능은 EditMode/자동 테스트로 닫고, 실제 PlayMode 또는 다중 PC 검증은 큰 흐름 단위로 묶는 기존 운영 원칙을 존중한다.
- 새 단계의 범위를 임의로 확대하지 않는다.
- 포트폴리오 관점에서 “무엇을 구현했고 무엇을 검증했는지”가 설명 가능하도록 문서화한다.

---

# 2. 프로젝트 최종 목표와 방향

## 2.1 단기 목표

학원 내부망에서 강사님이 지원하는 서버 또는 사용자 PC를 통해 학원 구성원들이 함께 접속해 플레이할 수 있는 실시간 루미큐브형 타일 게임을 배포한다.

## 2.2 장기 방향

단일 루미큐브 게임으로 끝내지 않고, 규칙이 간단한 여러 보드게임을 추가할 수 있는 **보드게임 플랫폼**으로 확장한다.

재사용 대상:

- 인증
- 사용자 프로필
- 로비
- 대기방
- 방장·참가자 관리
- 실시간 통신
- 게임 세션
- 배포 인프라
- 모니터링·운영 기반

분리 대상:

- 게임별 규칙
- 게임별 상태 전이
- 게임별 명령 처리
- 게임별 점수 및 종료 판정
- 게임별 UI

---

# 3. 기술 스택과 실행 환경

## 3.1 Backend

- Java 17 Temurin
- Spring Boot 3.5.x
- MySQL 8.4
- JWT Access/Refresh Token
- STOMP WebSocket
- Maven Wrapper

## 3.2 Frontend

- Vue 3
- Vite
- Pinia
- Axios
- STOMP WebSocket Client
- Production Nginx

## 3.3 Infrastructure

- Docker Desktop
- Docker Compose
- Docker Desktop Kubernetes
- kind 기반 Kubernetes
- `kubectl`
- MySQL StatefulSet + PVC
- Backend Deployment
- Frontend Deployment + NodePort
- Windows PowerShell Classroom LAN Script

## 3.4 주요 포트

- Kubernetes Frontend NodePort: `30517`
- Backend Container Port: `8080`
- Frontend Container Port: `80`
- MySQL Container Port: `3306`
- Docker Compose 사용 시 과거 Host Port:
  - Frontend `5173`
  - Backend `8080`
  - MySQL `33307`

학원 공용 실습에서 `8080`, `5173`이 자주 사용되므로 Kubernetes 외부 진입 포트는 `30517`로 분리했다.

---

# 4. Phase 진행 현황

## Phase 0 — Foundation

완료 내용:

- Docker/MySQL/Spring Boot/Vite 기동
- `/api/health` 확인
- STOMP 연결 확인
- 재연결 UI 보완

## Phase 1 — Tile Domain & Pure Rule Engine

완료 내용:

- 총 106타일
- 2~4인 초기 분배
- RUN/GROUP 판정
- 첫 등록 30점
- 손패 기여
- 테이블 재조합
- 조커 교체·회수·재사용
- CLASSIC/SPEED 규칙 분리
- 교착·점수 계산

검증:

- Backend 테스트 112개 통과
- 조커 우회, 순서 비결정성, Null ParticipantId 보완

## Phase 2 — Authentication & Profile

완료 내용:

- 회원가입/로그인
- BCrypt
- JWT Access/Refresh
- HttpOnly Refresh Cookie
- Refresh Token 회전
- 로그아웃 후 재발급 차단
- 사용자 프로필
- 401 발생 시 재발급 1회

검증:

- Backend 156개 통과
- Frontend 34개 통과
- BLOCKED/DELETED 차단
- `iat`, `exp`, `jti` 검증

## Phase 3 — Lobby & Waiting Room Foundation

완료 내용:

- 2·3·4인 CLASSIC 방 생성
- 방 목록 초기 REST 조회
- 방 목록 실시간 갱신
- 방 입장·나가기
- 사용자당 활성 방 1개 제한
- 정원 초과 및 동시 입장 경쟁 차단
- `seatOrder`
- 방장 위임
- 마지막 참가자 이탈 시 CLOSED
- 새로고침 후 대기방 복구
- READY
- 게임 시작 가능 여부 동기화

보안·안정성:

- STOMP Allowlist
- 허용되지 않은 SEND 차단
- `startable`, `startBlockReason` 동기화
- 실패 `actionId` 재실행 차단
- Error Frame
- 재연결 Subscription 중복 방지

검증:

- Backend 221개 통과
- Frontend 62개 통과
- TypeScript 통과
- Production Build 통과
- MySQL V2 정상
- 2계정 수동 검증 완료

## Phase 4 — Minimum Game Session And Initial Deal

완료 내용:

- 방에서 게임 세션 생성
- 동일 Game 진입
- 106타일 게임 상태
- 플레이어당 Rack 14개
- 2인 기준 Pool 78개
- 상대 Rack 상세 비공개
- 초기 상태 WebSocket 전달

검증:

- Backend 258개 통과
- Frontend 77개 통과
- TypeScript 통과
- Production Build 통과
- MySQL V3 정상
- 2계정 Runtime 정상

## Phase 5 — Minimum Current Turn, Draw And Pass

완료 내용:

- 현재 턴
- Draw
- Pass
- 상태 버전
- Action ID
- Frontend pending action 처리
- Reply/Private State 레이스 보완

주요 보완:

- `pendingActionId`
- `pendingBaseVersion`
- 잠금 쿼리에서 `GAME_NOT_FOUND` 오검출 보완
- 트랜잭션 및 동시성 처리 보완

판정:

- **Phase 5 FINAL**

## Phase 5.5-A — Dockerized Local Runtime

목표:

```text
docker compose up -d --build
```

결과:

- MySQL
- Backend
- Frontend + Nginx
- `/api/**`, `/ws` Backend Reverse Proxy
- Health 정상

판정:

- **FINAL**

## Phase 5.5-B — Local Kubernetes Runtime

구성:

- Namespace
- ConfigMap
- Secret
- MySQL Headless Service
- MySQL ClusterIP Service
- MySQL StatefulSet 1
- PVC 2Gi
- Backend Deployment 1
- Frontend Deployment 1
- Frontend NodePort `30517`
- InitContainer DB 대기
- Startup/Readiness/Liveness Probe
- Local image + `imagePullPolicy: Never`
- Resource Request/Limit
- Service Account Token 자동 마운트 비활성화
- SecurityContext

주요 결과:

- Backend 1/1 Running
- Frontend 1/1 Running
- MySQL 1/1 Running
- PVC Bound 및 데이터 보존 확인
- NodePort Health 정상
- MySQL Pod 재생성 후 사용자 데이터 보존 확인

판정:

- **FINAL**

## Phase 5.5-C — Classroom LAN Deployment Readiness

구성:

- `scripts/classroom-lan/`
- Self-test
- Firewall Enable
- Start
- Stop
- Status
- Test
- `127.0.0.1` + 선택 LAN IPv4 Bind
- CORS Origin 갱신
- Backend Rollout
- Runtime State/PID 관리
- LocalSubnet 방화벽 제한
- Kubernetes Workload/PVC 비파괴 종료

최종 판정:

- **FINAL PASS**

---

# 5. Phase 5.5-C 최종 Runtime 검증 결과

## 5.1 학원에서 검증된 내용

- PowerShell Self-test 통과
- 방화벽 규칙 생성 성공
- 방화벽 중복 실행 안전
- Classroom LAN Start 성공
- `docker-desktop` Context 확인
- `127.0.0.1` + 선택 LAN IPv4 Bind 성공
- CORS 갱신 및 Backend Rollout 성공
- Status 정상
- Test 정상
- localhost Health UP
- LAN IPv4 Health UP
- Database UP
- 중복 Start 시 기존 PID 유지
- Stop 시 Port-forward만 종료
- Kubernetes Workload/PVC 유지
- Stop 후 LAN URL 연결 실패 확인
- 재시작 시 새 PID로 정상 기동
- 다른 PC에서 접속 성공
- 다른 PC에서 방 생성·게임 시작 정상
- 로그아웃·재접속·새로고침·게임 회귀 정상

## 5.2 집에서 최종 재검증된 내용

환경:

```text
프로젝트 경로: E:\rumi\cube\phase
Kubernetes Context: docker-desktop
Kubernetes: kind v1.36.1
Node: desktop-control-plane
LAN IP: 192.168.1.101
NodePort: 30517
```

Kubernetes 최종 상태:

```text
backend-*    1/1 Running
frontend-*   1/1 Running
mysql-0      1/1 Running
```

Self-test:

```text
11 passed, 0 failed
```

Firewall:

```text
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
LocalSubnet only
TCP 30517
```

Start 결과:

```text
Classroom LAN bridge started successfully.
Context : docker-desktop
PID     : 23900
Local   : http://127.0.0.1:30517
Client  : http://192.168.1.101:30517
Health  : http://192.168.1.101:30517/health
CORS    : updated and backend rolled out
```

Test 결과:

```text
KubectlContext      : docker-desktop
MySqlReady          : True
BackendReady        : True
FrontendReady       : True
PortForwardPid      : 23900
PortListening       : True
FirewallLocalSubnet : True
LocalHealth         : UP
LanHealth           : UP
Database            : UP
ClientUrl           : http://192.168.1.101:30517
```

Stop 결과:

```text
Stopped Classroom LAN port-forward PID 23900.
Classroom LAN bridge stopped.
Kubernetes workloads and PVC were not changed.
```

Stop 이후 상태:

```text
KubectlContext     : docker-desktop
NamespaceExists    : True
MySqlReady         : True
BackendReady       : True
FrontendReady      : True
PortForwardPid     :
PortForwardAlive   : False
LanIp              :
HostUrl            :
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
HostHealthUp       : False
```

최종 의미:

- Classroom LAN Bridge만 종료됨
- Kubernetes Backend/Frontend/MySQL 유지
- PVC 영향 없음
- 방화벽 규칙 유지
- 외부 LAN 진입은 Bridge 종료로 차단
- 종료 동작까지 정상

---

# 6. Phase 5.5-C에서 실제 반영된 최종 스크립트 수정

## 6.1 `Start-ClassroomLan.ps1`

문제:

```powershell
$Port:
```

PowerShell 문자열 안에서 변수 뒤에 `:`가 붙어 잘못된 변수 참조로 파싱됐다.

최종 수정:

```powershell
${Port}:
```

수정 파일:

```text
scripts/classroom-lan/Start-ClassroomLan.ps1
```

## 6.2 `ClassroomLan.Common.psm1`

Windows PowerShell 5.1 환경에서 한글이 포함된 AccessDenied 판별 정규식이 잘못 저장되며 깨졌다.

깨진 정규식이 다음과 같은 형태로 변형됐다.

```text
?≪꽭??*嫄곕?
沅뚰븳.*嫄곕?
```

이로 인해 정규식 파서가 다음 오류를 발생시켰다.

```text
수량자 {x,y} 앞에 아무 것도 없습니다.
```

최종 수정:

- 비ASCII 한글 AccessDenied 패턴 제거
- ASCII 전용 판별 패턴 사용

대상 예시:

```powershell
(?i)(access\s+is\s+denied|accessdenied|permissiondenied|permission\s+denied|windows\s+system\s+error\s*5|system\s+error\s*5|error\s*5\b|unauthorized)
```

수정 파일:

```text
scripts/classroom-lan/ClassroomLan.Common.psm1
```

## 6.3 최종 수정본

이전 채팅에서 최종 수정된 문서 산출물:

```text
scripts_fixed.zip
```

새 채팅에서는 사용자가 업로드하는 최신 전체 소스에 이 수정이 실제 포함되어 있는지 확인한다.

---

# 7. Kubernetes Local Image 관련 중요 사실

Docker Desktop Images 화면 또는 다음 명령에 이미지가 보여도:

```powershell
docker images
```

kind Node 내부 containerd에는 이미지가 없을 수 있다.

현재 사용 이미지:

```text
realtime-tile-game-backend:local
realtime-tile-game-frontend:local
```

Deployment:

```yaml
imagePullPolicy: Never
```

따라서 Node containerd에 이미지가 없으면:

```text
ErrImageNeverPull
```

이 발생한다.

확인 명령:

```powershell
docker exec desktop-control-plane crictl images
```

Node 내부에 이미지가 없을 때 수행했던 해결:

```powershell
docker save realtime-tile-game-backend:local -o backend-local.tar
docker save realtime-tile-game-frontend:local -o frontend-local.tar

docker cp .\backend-local.tar desktop-control-plane:/backend-local.tar
docker cp .\frontend-local.tar desktop-control-plane:/frontend-local.tar

docker exec desktop-control-plane ctr -n k8s.io images import /backend-local.tar
docker exec desktop-control-plane ctr -n k8s.io images import /frontend-local.tar

docker exec desktop-control-plane crictl images
```

이후 Pod 재생성:

```powershell
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=backend
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=frontend
kubectl get pods -n realtime-tile-game -w
```

주의:

- Docker Host 이미지 저장소와 kind Node containerd 이미지 저장소는 동일하다고 가정하지 않는다.
- `imagePullPolicy: Never`는 현재 로컬 배포 전략에 맞으므로 문제를 피하려고 임의로 변경하지 않는다.
- Docker Hub Push로 우회하지 않는다.
- Kubernetes Reset, Namespace 삭제, PVC 삭제를 먼저 시도하지 않는다.
- `kind` CLI가 설치돼 있다면 `kind load docker-image` 사용을 검토할 수 있으나, 현재 집 환경에서는 CLI가 없어서 `ctr import`로 해결했다.

---

# 8. 주요 운영 명령

## 8.1 Kubernetes 상태

```powershell
kubectl config current-context
kubectl get nodes
kubectl get pods,pvc,svc -n realtime-tile-game
kubectl get deployment,statefulset -n realtime-tile-game
```

## 8.2 Classroom LAN Self-test

```powershell
cd E:\rumi\cube\phase
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

목표:

```text
11 passed, 0 failed
```

## 8.3 방화벽 규칙 생성

반드시 관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1
```

정상 결과:

```text
Firewall rule enabled: Realtime Tile Game Classroom LAN TCP 30517
Scope: inbound TCP, LocalSubnet only.
Windows Firewall was not disabled.
```

## 8.4 LAN 시작

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.1.101
```

다른 환경에서는 실제 유선 LAN IP를 사용한다.

IP 확인:

```powershell
Get-NetIPAddress -AddressFamily IPv4 -AddressState Preferred |
    Format-Table IPAddress, InterfaceAlias
```

선택 기준:

- 실제 이더넷 또는 Wi-Fi 어댑터
- WSL, Hyper-V, Docker 가상 어댑터 제외
- Loopback 제외

## 8.5 상태 확인

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

## 8.6 Host 검증

```powershell
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

목표:

```text
MySqlReady          : True
BackendReady        : True
FrontendReady       : True
PortListening       : True
FirewallLocalSubnet : True
LocalHealth         : UP
LanHealth           : UP
Database            : UP
```

## 8.7 LAN 종료

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

종료 후:

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

목표:

```text
MySqlReady       : True
BackendReady     : True
FrontendReady    : True
PortForwardAlive : False
HostHealthUp     : False
```

---

# 9. 현재 남아 있는 비차단 참고 사항

## 9.1 Stop 시 PID 재사용 경고

실제 출력:

```text
The original PID was reused while waiting for shutdown.
The new process will not be terminated.
```

판정:

- 실패 아님
- 원래 Port-forward 프로세스가 종료된 뒤 Windows가 같은 PID를 다른 프로세스에 재사용한 상황을 감지
- 관계없는 새 프로세스를 종료하지 않도록 보호한 정상 안전장치
- 최종 `PortForwardAlive : False`로 종료 성공 확인

## 9.2 두 번째 PC 안내 문구

`Test-ClassroomLan.ps1` 마지막에 다음 문구가 출력될 수 있다.

```text
A second classroom PC must still perform the documented client verification.
```

이것은 실패가 아니라 Host Script가 다른 PC의 브라우저 검증까지 자동 수행할 수 없어서 출력하는 안내다.

학원에서 실제 다른 PC 접속과 게임 흐름을 이미 검증했다.

## 9.3 방화벽 규칙

현재 규칙은 유지돼 있다.

```text
TCP 30517
Inbound
LocalSubnet only
```

Windows Firewall 전체를 끄지 않았다.

---

# 10. 새 채팅에서 가장 먼저 할 일

1. 사용자가 최신 전체 소스 ZIP을 업로드한다.
2. 이 인수인계 문서를 함께 첨부하거나 본문에 다음 짧은 프롬프트를 붙인다.
3. 새 채팅은 최신 전체본에서 다음을 확인한다.
   - `scripts/classroom-lan/Start-ClassroomLan.ps1`의 `${Port}` 수정 포함 여부
   - `ClassroomLan.Common.psm1`의 ASCII AccessDenied 정규식 포함 여부
   - Phase 5.5-C 문서 반영 여부
   - Clean Source ZIP 생성 기준
4. Phase 5.5 최종 문서화와 최신 Clean Source 정리가 끝났는지 확인한 뒤 다음 단계로 넘어간다.
5. 다음 단계가 Phase 6이라면 기존 로드맵·작업지시서를 먼저 확인하고 범위를 확정한다. 문서 없이 Phase 6 범위를 임의 생성하지 않는다.

---

# 11. 새 채팅에 붙여넣을 짧은 시작 프롬프트

```text
실시간 루미큐브형 타일 게임 프로젝트를 이어서 진행한다.

첨부한 인수인계 문서를 처음부터 끝까지 읽고 현재 상태를 정확히 이어간다.

현재 Phase 5.5-C Classroom LAN Deployment Readiness는 FINAL PASS다.

최종 확인:
- Self-test 11/11
- Kubernetes Backend/Frontend/MySQL 1/1 Running
- Firewall TCP 30517 LocalSubnet Match
- Start 성공
- localhost Health UP
- LAN Health UP
- Database UP
- CORS 갱신 및 Backend Rollout 성공
- Stop 성공
- Stop 후 PortForwardAlive False
- Kubernetes Workload/PVC 유지
- 학원 다른 PC 접속·방 생성·게임 시작·재접속 검증 완료

집 환경:
- E:\rumi\cube\phase
- Docker Desktop Kubernetes kind v1.36.1
- docker-desktop context
- 1 node: desktop-control-plane
- LAN IP 검증값: 192.168.1.101
- NodePort: 30517

최신 전체 소스 ZIP을 유일한 코드 기준으로 삼고,
먼저 Phase 5.5-C 최종 수정과 문서 반영 상태를 점검한 뒤 다음 작업을 확정한다.

코드 변경은 Patch ZIP,
문서는 여러 파일을 하나의 문서 전용 ZIP으로 제공한다.
과거 전체본보다 지금 첨부한 최신 전체본을 우선한다.
```

---

# 12. 현재 최종 판정

```text
Phase 0      FINAL
Phase 1      FINAL
Phase 2      FINAL
Phase 3      FINAL
Phase 4      FINAL
Phase 5      FINAL
Phase 5.5-A  FINAL
Phase 5.5-B  FINAL
Phase 5.5-C  FINAL PASS
```

현재 프로젝트는 로컬 개발 실행, Docker Compose 실행, Kubernetes 실행, 학원 내부망 진입 준비까지 검증된 상태다.

다음 채팅에서는 이 상태를 다시 처음부터 재검증하기보다, 최신 소스와 문서 반영을 확인하고 다음 개발 단계 또는 Phase 5.5 최종 패키징으로 이어간다.
