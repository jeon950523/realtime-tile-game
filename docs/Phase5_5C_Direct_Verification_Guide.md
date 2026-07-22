# Phase 5.5-C 직접 검증 가이드

작성 기준: 2026-07-16 KST

## 1. 전제 확인 — Host PC

프로젝트 루트에서 실행한다.

```powershell
kubectl config current-context
kubectl get nodes
kubectl get pods -n realtime-tile-game
kubectl get pvc -n realtime-tile-game
```

기대:

```text
context docker-desktop
mysql/backend/frontend Ready 1/1
PVC Bound
```

Docker Desktop kind 3 Node에서 Local Image가 필요한 경우:

```powershell
kind load docker-image `
  realtime-tile-game-backend:local `
  realtime-tile-game-frontend:local `
  --name desktop
```

## 2. Host 회귀 테스트

Backend:

```powershell
cd backend
.\mvnw.cmd clean test
cd ..
```

Frontend:

```powershell
cd frontend
npm ci
npm run check
cd ..
```

실제 테스트 수를 기록한다.

## 3. Classroom LAN 순수 로직 Self-test

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

기대:

```text
10 passed, 0 failed
```

## 4. Host LAN IPv4 확인

```powershell
Get-NetIPAddress -AddressFamily IPv4 -AddressState Preferred |
  Format-Table IPAddress,InterfaceAlias,SkipAsSource
```

학원 Wi-Fi 또는 Ethernet의 RFC1918 주소를 선택한다.

제외:

```text
Docker
WSL
vEthernet
Hyper-V
VPN
Loopback
169.254.x.x
```

후속 예시에서는 `192.168.0.10`을 실제 값으로 교체한다.

## 5. Firewall Rule 생성

관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1 -Port 30517
```

두 번 실행해도 Rule이 중복 생성되지 않아야 한다.

확인:

```powershell
Get-NetFirewallRule -Name RealtimeTileGame-ClassroomLan-TCP-30517
```

기대:

```text
Enabled True
Direction Inbound
Action Allow
Group Realtime Tile Game Classroom LAN
RemoteAddress LocalSubnet
```

일반 권한 PowerShell에서 `Get-NetFirewallRule` 조회가 차단될 수 있다. 이 경우 Rule이 실제로 존재해도 존재 여부를 확인할 수 없으므로 `NotFound`로 판단하지 않는다.

```text
QuerySucceeded False
Exists
IsValid False
Reason AccessDenied
```

## 6. Start

일반 권장 실행은 관리자 PowerShell이다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

기대:

```text
Backend Rollout 성공
PID 출력
Local http://127.0.0.1:30517
Client http://192.168.0.10:30517
두 Health UP
```

ConfigMap 확인:

```powershell
kubectl get configmap realtime-tile-game-config `
  -n realtime-tile-game `
  -o jsonpath='{.data.CORS_ALLOWED_ORIGINS}'
```

정확한 세 Origin만 있어야 한다.

중복 Start:

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

기존 PID를 재사용하고 새 Process를 만들지 않아야 한다.

## 7. Status와 Host Test

빠른 재검증은 관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

관리자 Status 기대:

```text
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
```

일반 권한 조회가 차단된 환경의 Status 기대:

```text
FirewallRuleExists :
FirewallRuleValid  : False
FirewallReason     : AccessDenied
```

`AccessDenied`는 Rule이 실제로 없음을 뜻하는 `NotFound`와 다르다.

추가 확인:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 30517
Invoke-RestMethod http://127.0.0.1:30517/api/health
Invoke-RestMethod http://192.168.0.10:30517/api/health
```

Health:

```text
success true
status UP
database UP
```

## 8. 두 번째 Client PC

같은 학원 내부망의 다른 Windows PC에서 실행한다.

```powershell
Test-NetConnection 192.168.0.10 -Port 30517
Invoke-RestMethod http://192.168.0.10:30517/api/health
```

기대:

```text
TcpTestSucceeded True
status UP
database UP
```

브라우저:

```text
http://192.168.0.10:30517
http://192.168.0.10:30517/health
```

DevTools Network:

```text
/api/** Host 192.168.0.10:30517
/ws Host 192.168.0.10:30517
```

Backend `8080`이나 MySQL `3306`에 직접 접근하면 실패다.

## 9. 기능 회귀 — Host + Client

```text
회원가입
로그인
Refresh Cookie
F5 인증 복구
프로필
방 목록 실시간 갱신
2계정 방 입장
READY
START
Rack 14/14
Pool 78
현재 턴 DRAW
Pool 77
Rack +1
턴 전환
상대 Rack Privacy
양쪽 F5 복구
Console Error 0
```

## 10. 죽은 PID 복구 검증

Start 후 Task Manager 또는 다음 명령으로 관리 PID만 강제 종료한다.

```powershell
$status = Get-Content .runtime\classroom-lan\port-forward.json -Raw | ConvertFrom-Json
Stop-Process -Id $status.Pid
```

다시 Start:

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

죽은 State를 정리하고 새 PID로 시작해야 한다.

## 11. IP 변경 검증

실제 Host에 두 번째 유효 LAN IP가 있을 때만 수행한다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp <NEW_HOST_LAN_IPV4>
```

기대:

```text
기존 관리 Process 안전 종료
새 PID 시작
ConfigMap에서 이전 LAN Origin 제거
새 LAN Origin 한 개만 추가
localhost·127.0.0.1 유지
```

## 12. Stop

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

기대:

```text
Port-forward Process 종료
.runtime State 삭제
Kubernetes Workload 유지
PVC 유지
```

Firewall도 제거할 때만 관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Disable-ClassroomLanFirewall.ps1 -Port 30517
```

## 13. Firewall Runtime 보완 빠른 재검증

관리자 PowerShell:

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp <HOST_LAN_IPV4>
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

기대:

```text
Self-test 11 passed, 0 failed
Start 기존 PID 재사용 또는 정상 새 기동
FirewallRuleExists True
FirewallRuleValid True
FirewallReason Match
localhost Health UP
LAN Health UP
Database UP
```

별도 일반 권한 PowerShell에서 Status를 한 번 실행해 조회가 차단되는 환경이면 다음 분류를 확인한다.

```text
FirewallRuleExists 빈 값
FirewallRuleValid False
FirewallReason AccessDenied
```

이번 수정은 Firewall 오류 분류만 변경하므로 이미 통과한 전체 Client 게임 회귀는 반복하지 않아도 된다.

## 14. 실제 Runtime 통과 기록

```text
Self-test 10/10(보완 전)
Firewall 생성·중복 실행
Start·CORS·Backend Rollout
localhost/LAN Health·Database
중복 Start PID
Status·Test
Stop·재시작
Client PC 접속
방 생성·게임 시작
로그아웃 후 재접속
```

## 15. 최종 기록

```text
Backend Tests:
Frontend Tests:
TypeScript:
Production Build:
PowerShell Self-test:
LAN IPv4:
Firewall First Enable:
Firewall Duplicate Enable:
Start:
Backend Rollout:
ConfigMap Origins:
Host Local Health:
Host LAN Health:
Duplicate Start PID:
Dead PID Recovery:
Status:
Client TCP:
Client REST:
Client WebSocket:
Authentication:
2-account Game:
F5 Recovery:
Console Error:
Stop:
Firewall Disable:
```
