# Phase 5.5-C Classroom LAN Architecture and Operations

작성 기준: 2026-07-16 KST

## 1. 네트워크 구조

```text
Classroom Client Browser
Origin http://HOST_LAN_IPV4:30517
        │
        ▼
Windows Host Firewall
Inbound TCP 30517 / RemoteAddress LocalSubnet
        │
        ▼
Managed kubectl port-forward
127.0.0.1 + HOST_LAN_IPV4
service/frontend 30517:80
        │
        ▼
Frontend Service / Nginx
/api/** → backend:8080
/ws     → backend:8080
        │
        ▼
Backend ClusterIP / Deployment replicas 1 / Recreate
        │
        ▼
MySQL ClusterIP / StatefulSet / PVC
```

Frontend Same-origin 규칙은 유지된다. Client Browser는 Backend `8080`과 MySQL `3306`을 직접 호출하지 않는다.

## 2. LAN IPv4 선택

자동 탐색은 안전 후보가 정확히 하나일 때만 사용한다.

허용:

```text
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
Windows Host에 실제 할당된 Preferred IPv4
```

제외 Adapter 이름:

```text
Docker
WSL
Hyper-V
vEthernet
Loopback
VPN
Tailscale
ZeroTier
VirtualBox
VMware
Bluetooth
Container
```

후보가 여러 개면 사용자가 `-LanIp`으로 명시해야 한다. 임의의 첫 주소를 사용하지 않는다.

## 3. Origin Runtime 갱신

선택 방식:

```text
Cluster ConfigMap Merge Patch
→ Backend rollout restart
→ rollout status --timeout=180s
```

매번 정확한 세 Origin을 새로 계산한다.

```text
localhost
127.0.0.1
현재 선택 LAN IPv4
```

따라서 중복 누적이 없고 IP가 바뀌면 이전 LAN Origin은 제거된다.

Source Manifest에는 실제 학원 IP를 저장하지 않는다. `kubectl apply -f k8s/`를 다시 수행하면 Source 기본값으로 돌아갈 수 있으므로 Classroom LAN Start를 다시 실행한다.

Stop은 ConfigMap을 되돌리지 않는다. Port-forward만 종료한다. 다음 Start가 현재 IP 기준으로 Origin을 다시 정확하게 교체한다.

## 4. 관리형 Port-forward

실행:

```text
kubectl port-forward service/frontend 30517:80
--namespace realtime-tile-game
--address=127.0.0.1,HOST_LAN_IPV4
```

저장 정보:

```text
PID
Process 시작 UTC
LAN IPv4
Port
Namespace
시작 UTC
Host/Local URL
stdout/stderr Log 경로
```

저장 위치:

```text
.runtime/classroom-lan/
```

Secret과 JWT 값은 저장하지 않는다.

### 중복 실행

```text
State 정상 + PID/StartTime/CommandLine 일치 + 설정 동일
→ 기존 Process 재사용
```

### 설정 변경

```text
정상 관리 Process + LAN IP/Port/Namespace 변경
→ 검증된 Process만 종료
→ 새 설정으로 시작
```

### 죽은 PID 또는 PID 재사용

```text
Process 없음
CommandLine 불일치
StartTime 불일치
실행 파일이 kubectl이 아님
→ State 정리
→ 무관 Process 종료 금지
```

Stop 후 대기 중 PID가 재사용돼도 새 Process를 Force Kill하지 않는다.

## 5. Firewall

프로젝트 Rule:

```text
Name RealtimeTileGame-ClassroomLan-TCP-30517
Group Realtime Tile Game Classroom LAN
Direction Inbound
Protocol TCP
LocalPort 30517
RemoteAddress LocalSubnet
Action Allow
```

Enable은 관리자 권한을 요구한다. 동일한 정상 Rule이면 no-op이다. 같은 프로젝트 이름이지만 설정이 다르면 프로젝트 Group 여부를 확인한 뒤에만 재생성한다.

Firewall 조회 결과는 존재 여부와 조회 성공 여부를 분리한다.

```text
Match                 QuerySucceeded=True  Exists=True  IsValid=True
ConfigurationMismatch QuerySucceeded=True  Exists=True  IsValid=False
NotFound              QuerySucceeded=True  Exists=False IsValid=False
AccessDenied          QuerySucceeded=False Exists=null  IsValid=False
```

일반 권한 PowerShell에서 `PermissionDenied / Windows System Error 5`가 발생하면 정상 Rule을 `NotFound`로 오인하지 않는다. Status는 `FirewallRuleExists`를 빈 값으로 표시하고 `FirewallReason AccessDenied`를 출력한다.

Disable은 정확한 Rule Name과 Group만 삭제한다. Windows Firewall 전체 비활성화, `RemoteAddress Any`, 다른 Rule 삭제는 수행하지 않는다.

## 6. Host 운영 순서

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1
```

일반 권장 실행은 관리자 PowerShell이다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp <HOST_LAN_IPV4>
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

일반 권한에서 Firewall 조회가 허용되는 Windows 환경에서는 그대로 동작할 수 있다. 조회가 거부되면 Start/Test는 관리자 실행을 정확히 안내하고 Status는 `AccessDenied`를 표시한다. `-SkipFirewallCheck`는 의도적인 로컬 진단용이며 일반 운영 우회책이 아니다.

종료:

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

Firewall 제거가 필요할 때만 관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Disable-ClassroomLanFirewall.ps1
```

## 7. Client 운영 순서

```powershell
Test-NetConnection <HOST_LAN_IPV4> -Port 30517
Invoke-RestMethod http://<HOST_LAN_IPV4>:30517/api/health
```

브라우저에서 `/health`, 인증, 2계정 게임 흐름과 WebSocket을 확인한다.

## 8. kind 3 Node 주의사항

Docker Desktop Kubernetes가 다음 Node를 사용할 수 있다.

```text
desktop-control-plane
desktop-worker
desktop-worker2
```

`imagePullPolicy: Never`이므로 Pod가 배치될 모든 Node가 Local Image를 볼 수 있어야 한다.

```powershell
kind load docker-image `
  realtime-tile-game-backend:local `
  realtime-tile-game-frontend:local `
  --name desktop
```

Phase 5.5-C 스크립트는 Image Build·Load·Manifest Apply를 자동화하지 않는다. 기존 Phase 5.5-B Runtime이 Ready인 상태를 전제로 한다.

## 9. 장애 진단

### Host Port 실패

```text
Get-ClassroomLanStatus
Runtime State/PID
Get-NetTCPConnection 30517
port-forward stderr Log
Frontend Service/Pod Ready
```

### Client Port 실패

```text
Host LAN IPv4 변경
Firewall Rule
동일 Subnet/VLAN
AP Client Isolation
Windows Network Profile
보안 프로그램
```

### REST 정상, WebSocket 실패

```text
ConfigMap의 정확한 LAN Origin
Backend Rollout 완료
Nginx /ws Upgrade
Browser Console
Backend WebSocket Log
```

환경 문제를 애플리케이션 코드 변경으로 우회하지 않는다.

## 10. 보안 경계

```text
신뢰 가능한 학원 LocalSubnet 전용
TLS 없음
공인 Domain 없음
공유기 Port Forwarding 없음
인터넷 공개 없음
Backend/MySQL 직접 노출 없음
실제 Secret 파일 없음
```

학원망이 Public Network Profile이거나 Client 격리 정책을 쓰면 관리자·네트워크 담당자 확인이 필요하다.


## 11. 실제 Host/Client Runtime 검증

실제 환경에서 다음 흐름이 확인됐다.

```text
Firewall 생성·중복 실행 안전
Start와 정확한 두 Address Bind
CORS 갱신과 Backend Recreate Rollout
Status·Test·Health·Database UP
중복 Start PID 유지
Stop 후 관리 Port-forward만 종료
Workload/PVC 유지
재시작 시 새 PID
Client PC 접속
Client PC 방 생성·게임 시작
로그아웃 후 재접속
```

일반 권한 Firewall 조회에서 `Windows System Error 5`가 발생한 것이 이번 Runtime 보완의 원인이다. 보완 후 관리자 Self-test 11/11과 Start·Status·Test를 재검증한 뒤 독립 FINAL 검수를 진행한다.
