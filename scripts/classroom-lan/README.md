# Classroom LAN 운영 스크립트

Phase 5.5-C는 Docker Desktop Kubernetes의 Frontend Service를 Windows Host의 선택된 사설 LAN IPv4에 안전하게 연결한다.

```text
Client PC
→ http://HOST_LAN_IPV4:30517
→ managed kubectl port-forward
→ frontend Service:80
→ Nginx /api·/ws same-origin proxy
→ Backend
→ MySQL
```

## 전제

```text
Windows PowerShell 5.1 또는 PowerShell 7
kubectl context docker-desktop
realtime-tile-game Namespace 배포 완료
mysql/backend/frontend Ready 1/1
```

## 1. 순수 로직 Self-test

관리자 권한과 Kubernetes가 없어도 실행 가능하다.

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

기대:

```text
Classroom LAN self-test result: 11 passed, 0 failed
```

검증 범위:

```text
잘못된 IPv4 차단
복수 Adapter 강제 선택
Origin 중복 제거와 IP 교체
중복 Start 판정
죽은 PID·다른 Process 거절
Firewall 중복 생성 판정
Firewall PermissionDenied를 AccessDenied로 분류
Status에서 확인 불가능한 존재 여부를 빈 값으로 유지
Status 민감정보 비노출
```

## 2. Firewall 활성화

관리자 PowerShell에서 한 번 실행한다.

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1 -Port 30517
```

규칙:

```text
Inbound TCP 30517
RemoteAddress LocalSubnet
프로젝트 전용 Rule Name과 Group
```

Windows Firewall 전체를 비활성화하지 않는다.

## 3. Classroom LAN 시작

LAN IPv4를 명시하고 **관리자 PowerShell**에서 실행하는 방식을 권장한다. Firewall Rule 조회가 일반 권한에서 차단되는 Windows 환경이 있기 때문이다.

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

일반 권한에서 조회가 차단되면 Start는 `NotFound`나 설정 오류로 오인하지 않고 다음 의미로 실패한다.

```text
Firewall verification requires an elevated PowerShell.
Run Start-ClassroomLan.ps1 as administrator.
```

`-SkipFirewallCheck`는 의도적인 로컬 진단에만 사용한다.

`-LanIp` 생략 시 물리 LAN Adapter 후보가 정확히 하나일 때만 자동 선택한다. 후보가 여러 개면 임의 선택하지 않고 실패한다.

Start는 다음을 수행한다.

```text
kubectl·context·Namespace·Workload Ready 확인
정확한 localhost/127.0.0.1/LAN Origin으로 ConfigMap 교체
필요한 경우 Backend Recreate Rollout
127.0.0.1과 선택 LAN IPv4에 port-forward 바인딩
PID·시작 시각·URL을 .runtime/classroom-lan에 저장
두 Host URL의 /api/health 확인
```

전체 인터페이스 `0.0.0.0`에는 바인딩하지 않는다.

이미 동일 설정으로 실행 중이면 새 Process를 만들지 않는다. IP나 Port가 변경되면 검증된 기존 Process만 종료하고 교체한다. PID가 다른 Process로 재사용된 경우 해당 Process는 종료하지 않는다.

## 4. 상태와 Host 검증

빠른 운영 검증은 관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

일반 권한 Status에서 Firewall 조회가 거부되면 다음처럼 존재 여부를 알 수 없는 상태로 출력한다.

```text
FirewallRuleExists :
FirewallRuleValid  : False
FirewallReason     : AccessDenied
```

`AccessDenied`는 정상 조회 후 Rule이 없다는 `NotFound`와 구분된다. Status에는 Secret·Password·JWT 값이 출력되지 않는다.

## 5. Client PC 검증

두 번째 PC에서 실행한다.

```powershell
Test-NetConnection 192.168.0.10 -Port 30517
Invoke-RestMethod http://192.168.0.10:30517/api/health
```

브라우저:

```text
http://192.168.0.10:30517
http://192.168.0.10:30517/health
```

## 6. 중지

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

관리되는 port-forward Process와 Runtime State만 정리한다. Namespace, Deployment, StatefulSet, PVC, Docker Process는 건드리지 않는다.

Firewall 규칙까지 제거할 때만 관리자 PowerShell에서 실행한다.

```powershell
.\scripts\classroom-lan\Disable-ClassroomLanFirewall.ps1 -Port 30517
```

## 장애 분리

Host에서는 정상이나 Client가 접속하지 못하면 다음 순서로 확인한다.

```text
Status의 PID와 Host Health
TCP 30517 Listen
Firewall LocalSubnet Rule
Host LAN IPv4 변경
동일 Subnet/VLAN 여부
학원 AP Client Isolation
Windows Network Profile·보안 프로그램
```

REST는 되지만 WebSocket만 실패하면 ConfigMap의 정확한 LAN Origin, Backend Rollout, Nginx `/ws` Upgrade, Browser Console과 Backend Log를 확인한다.


## 실제 Runtime 검증 기록

다음은 실제 Host/Client 환경에서 확인됐다.

```text
Self-test 10/10(AccessDenied Case 추가 전)
Firewall 생성·중복 실행 안전
Start·CORS 갱신·Backend Rollout
localhost/LAN Health와 Database UP
중복 Start PID 유지
Status·Test 정상
Stop은 Port-forward만 종료하고 Workload/PVC 유지
Stop 후 LAN URL 차단
재시작 시 새 PID
Client PC 접속·방 생성·게임 시작
로그아웃 후 재접속
```

이번 보완은 Firewall 조회 오류 분류만 변경한다. 보완 후 관리자 PowerShell에서 Self-test 11/11과 Start·Status·Test를 빠르게 재검증한다.
