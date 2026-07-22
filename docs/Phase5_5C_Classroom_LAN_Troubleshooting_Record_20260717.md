# Phase 5.5-C Classroom LAN 및 Docker Desktop Kubernetes 트러블슈팅 기록

## 0. 문서 목적

이 문서는 2026-07-17 집 환경 재배포 및 Phase 5.5-C 최종 Runtime 재검증 중 발생한 문제를 재현 가능한 형태로 정리한다.

대상 환경:

```text
OS: Windows
Shell: Windows PowerShell 5.1
프로젝트: E:\rumi\cube\phase
Docker Context: desktop-linux
Kubernetes Context: docker-desktop
Kubernetes Provider: kind
Kubernetes Version: v1.36.1
Node 수: 1
Node Container: desktop-control-plane
Namespace: realtime-tile-game
Frontend NodePort: 30517
LAN IP: 192.168.1.101
```

최종 결과:

```text
Backend  1/1 Running
Frontend 1/1 Running
MySQL    1/1 Running
Self-test 11/11
Local Health UP
LAN Health UP
Database UP
Start/Stop 정상
Kubernetes Workload/PVC 유지
```

---

# 1. 장애 요약표

| 번호 | 증상 | 실제 원인 | 해결 |
|---|---|---|---|
| 1 | Backend/Frontend `ErrImageNeverPull` | Docker Host에는 이미지가 있지만 kind Node containerd에는 없음 | `docker save` → Node에 복사 → `ctr -n k8s.io images import` |
| 2 | `docker cp` 성공 후 `/tmp/*.tar` 없음 | Docker Desktop kind Node에서 해당 경로 확인이 일관되지 않음 | `/backend-local.tar`, `/frontend-local.tar` 루트 경로 사용 |
| 3 | Classroom LAN Script를 찾지 못함 | PowerShell 현재 위치가 `C:\Windows\System32` | `cd E:\rumi\cube\phase` 후 실행 |
| 4 | `$Port:` ParserError | PowerShell 문자열에서 변수 뒤 `:`가 잘못 해석됨 | `$Port:` → `${Port}:` |
| 5 | AccessDenied 정규식 파싱 오류 | Windows PowerShell 5.1의 `Get-Content/Set-Content` 처리 중 한글 인코딩 손상 | 원본 복구 후 ASCII 전용 정규식 사용 |
| 6 | Firewall Rule Missing | 방화벽 규칙이 아직 생성되지 않음 | 관리자 PowerShell에서 `Enable-ClassroomLanFirewall.ps1` |
| 7 | Stop 시 PID reused 경고 | 종료 직후 같은 PID가 다른 프로세스에 재할당됨 | 새 프로세스를 죽이지 않는 안전장치, 최종 상태로 성공 판정 |
| 8 | 초기 PVC/MySQL Pending | 새 kind Cluster 초기 프로비저닝 중 | StorageClass/Provisioner 대기 후 Running 확인 |

---

# 2. 장애 1 — `ErrImageNeverPull`

## 2.1 증상

Kubernetes 배포 직후:

```text
backend-*    0/1 ErrImageNeverPull
frontend-*   0/1 ErrImageNeverPull
mysql-0      1/1 Running
```

Docker Host에서는 이미지가 존재했다.

```powershell
docker images
```

결과:

```text
realtime-tile-game-backend:local
realtime-tile-game-frontend:local
```

Deployment도 정확했다.

```yaml
image: realtime-tile-game-backend:local
imagePullPolicy: Never
```

```yaml
image: realtime-tile-game-frontend:local
imagePullPolicy: Never
```

## 2.2 오진하기 쉬운 부분

다음 사실만 보고 이미지가 Kubernetes에서도 사용 가능하다고 판단하면 안 된다.

```powershell
docker images
```

Docker Desktop Host Image Store와 kind Node의 containerd Image Store는 별개일 수 있다.

## 2.3 진단

Node 확인:

```powershell
docker ps
```

Node 컨테이너:

```text
desktop-control-plane
```

Node 내부 이미지 확인:

```powershell
docker exec desktop-control-plane crictl images
```

결과:

- MySQL과 Kubernetes System Image는 존재
- `realtime-tile-game-backend`
- `realtime-tile-game-frontend`

두 프로젝트 이미지가 없음

따라서 원인 확정:

```text
Docker Host Image Store에는 존재
kind Node containerd에는 없음
imagePullPolicy: Never
→ 외부 Pull도 하지 않음
→ ErrImageNeverPull
```

## 2.4 해결

Host 이미지를 tar로 저장:

```powershell
docker save realtime-tile-game-backend:local -o backend-local.tar
docker save realtime-tile-game-frontend:local -o frontend-local.tar
```

Node로 복사:

```powershell
docker cp .\backend-local.tar desktop-control-plane:/backend-local.tar
docker cp .\frontend-local.tar desktop-control-plane:/frontend-local.tar
```

Node containerd에 import:

```powershell
docker exec desktop-control-plane ctr -n k8s.io images import /backend-local.tar
docker exec desktop-control-plane ctr -n k8s.io images import /frontend-local.tar
```

확인:

```powershell
docker exec desktop-control-plane crictl images
```

Pod 재생성:

```powershell
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=backend
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=frontend
kubectl get pods -n realtime-tile-game -w
```

최종:

```text
backend-*    1/1 Running
frontend-*   1/1 Running
mysql-0      1/1 Running
```

## 2.5 재발 방지

로컬 이미지를 다시 빌드하거나 Cluster가 Reset된 경우 Node Image Store를 다시 확인한다.

```powershell
docker exec desktop-control-plane crictl images
```

이미지가 없으면 import를 반복한다.

`kind` CLI가 설치된 환경에서는 다음 방식도 가능하다.

```powershell
kind load docker-image realtime-tile-game-backend:local realtime-tile-game-frontend:local --name desktop
```

단, 실제 Cluster 이름을 먼저 확인해야 한다.

```powershell
kind get clusters
```

현재 집 환경에서는 `kind` CLI가 없어 직접 `ctr import`를 사용했다.

---

# 3. 장애 2 — `/tmp`로 복사한 tar가 import 시 보이지 않음

## 3.1 증상

```powershell
docker cp .\backend-local.tar desktop-control-plane:/tmp/backend-local.tar
```

출력:

```text
Successfully copied
```

하지만:

```powershell
docker exec desktop-control-plane ctr -n k8s.io images import /tmp/backend-local.tar
```

결과:

```text
ctr: open /tmp/backend-local.tar: no such file or directory
```

Frontend도 동일했다.

## 3.2 해결

`/tmp` 대신 Node 컨테이너 루트 경로를 사용했다.

```powershell
docker cp .\backend-local.tar desktop-control-plane:/backend-local.tar
docker cp .\frontend-local.tar desktop-control-plane:/frontend-local.tar
```

확인:

```powershell
docker exec desktop-control-plane ls -lh /backend-local.tar /frontend-local.tar
```

Import:

```powershell
docker exec desktop-control-plane ctr -n k8s.io images import /backend-local.tar
docker exec desktop-control-plane ctr -n k8s.io images import /frontend-local.tar
```

## 3.3 교훈

`docker cp`의 성공 출력만 믿지 말고 Import 전에 실제 파일을 확인한다.

```powershell
docker exec desktop-control-plane ls -lh <복사 경로>
```

---

# 4. 장애 3 — Script 경로를 찾지 못함

## 4.1 증상

PowerShell 위치:

```text
C:\Windows\System32
```

실행:

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1
```

오류:

```text
스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다.
```

## 4.2 원인

상대 경로는 현재 디렉터리를 기준으로 한다.

`C:\Windows\System32` 아래에는 프로젝트의 `scripts` 폴더가 없다.

## 4.3 해결

```powershell
cd E:\rumi\cube\phase
```

확인:

```powershell
Get-Location
```

그다음 실행:

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.1.101
```

---

# 5. 장애 4 — `$Port:` ParserError

## 5.1 증상

```text
변수 참조가 잘못되었습니다.
':' 뒤에 올바른 변수 이름 문자가 없습니다.
${}를 사용하여 이름을 구분하십시오.
```

문제 파일:

```text
scripts/classroom-lan/Start-ClassroomLan.ps1
```

문제 코드:

```powershell
throw "Firewall verification failed for TCP $Port: $($firewall.Reason). ..."
```

## 5.2 원인

PowerShell Double-Quoted String 안에서 변수 바로 뒤에 `:`를 붙이면 Scope 또는 Drive 형식처럼 해석될 수 있다.

## 5.3 해결

```powershell
throw "Firewall verification failed for TCP ${Port}: $($firewall.Reason). ..."
```

핵심:

```text
$Port:
→
${Port}:
```

## 5.4 검색 명령

```powershell
Select-String `
  -Path .\scripts\classroom-lan\*.ps1,.\scripts\classroom-lan\*.psm1 `
  -Pattern '\$[A-Za-z_][A-Za-z0-9_]*:'
```

주의:

아래는 정상 PowerShell Scope 문법이므로 수정하지 않는다.

```powershell
$script:Passed
$script:Failed
$script:FirewallGroup
$env:PATH
$global:Variable
```

---

# 6. 장애 5 — Windows PowerShell 5.1 인코딩으로 정규식 손상

## 6.1 발생 과정

`Start-ClassroomLan.ps1`의 `$Port:`를 자동 교체하려고 다음과 유사한 명령을 실행했다.

```powershell
(Get-Content <파일> -Raw).Replace(...) |
    Set-Content <파일> -Encoding UTF8
```

그 뒤 AccessDenied 판별 정규식의 한글 부분이 깨졌다.

깨진 예:

```text
?≪꽭??*嫄곕?
沅뚰븳.*嫄곕?
```

오류:

```text
구문 분석 - 수량자 {x,y} 앞에 아무 것도 없습니다.
```

## 6.2 원인

Windows PowerShell 5.1의 기본 인코딩 처리와 원본 파일 인코딩이 맞지 않아 비ASCII 한글 문자열이 손상됐다.

손상된 `?`가 정규식 수량자로 해석돼 Regex Parser가 실패했다.

## 6.3 최종 해결

1. 원본 Script ZIP을 다시 덮어써 손상 전 상태로 복구
2. `Start-ClassroomLan.ps1`은 VS Code에서 직접 `${Port}`로 수정
3. `ClassroomLan.Common.psm1`의 AccessDenied 정규식을 ASCII 전용으로 변경

최종 형태 예시:

```powershell
(?i)(access\s+is\s+denied|accessdenied|permissiondenied|permission\s+denied|windows\s+system\s+error\s*5|system\s+error\s*5|error\s*5\b|unauthorized)
```

## 6.4 재발 방지

- Windows PowerShell 5.1에서 비ASCII Script를 `Get-Content | Set-Content`로 재저장하지 않는다.
- VS Code에서 UTF-8 인코딩을 확인하고 직접 수정한다.
- 가능하면 정규식·오류 판별 패턴은 ASCII 기반으로 유지한다.
- 전체 Script 정적 파싱 검사를 Self-test에 추가하는 것을 권장한다.

예시 검사:

```powershell
$files = Get-ChildItem .\scripts\classroom-lan\*.ps1, .\scripts\classroom-lan\*.psm1

foreach ($file in $files) {
    $tokens = $null
    $errors = $null

    [System.Management.Automation.Language.Parser]::ParseFile(
        $file.FullName,
        [ref]$tokens,
        [ref]$errors
    ) | Out-Null

    if ($errors.Count -gt 0) {
        Write-Host "FAIL: $($file.Name)"
        $errors | ForEach-Object {
            Write-Host "  $($_.Message)"
        }
    }
    else {
        Write-Host "PASS: $($file.Name)"
    }
}
```

> 이 Parser 검사는 재발 방지 권장 사항이며, 현재 최종 Script에 자동 포함됐다고 단정하지 않는다.

---

# 7. 장애 6 — Firewall Rule Missing

## 7.1 증상

Self-test는 통과했지만 Start 시 다음 오류가 발생했다.

```text
The project firewall rule is missing for TCP 30517.
Run Enable-ClassroomLanFirewall.ps1 in an elevated PowerShell.
```

## 7.2 원인

Script 오류가 아니라 방화벽 규칙이 아직 생성되지 않은 정상 사전조건 실패다.

## 7.3 해결

관리자 PowerShell에서:

```powershell
cd E:\rumi\cube\phase
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1
```

결과:

```text
Firewall rule enabled: Realtime Tile Game Classroom LAN TCP 30517
Scope: inbound TCP, LocalSubnet only.
Windows Firewall was not disabled.
```

상태:

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

정상:

```text
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
```

## 7.4 보안 기준

허용 범위:

```text
Inbound
TCP 30517
LocalSubnet only
```

금지:

- Windows Firewall 전체 비활성화
- Any RemoteAddress로 확대
- 인터넷 공개
- 불필요한 추가 포트 허용

---

# 8. 장애 7 — Stop 시 PID reused 경고

## 8.1 출력

```text
The original PID was reused while waiting for shutdown.
The new process will not be terminated.
Stopped Classroom LAN port-forward PID 23900.
Classroom LAN bridge stopped.
Kubernetes workloads and PVC were not changed.
```

## 8.2 의미

원래 Port-forward 프로세스가 종료된 직후 Windows가 같은 PID를 다른 프로세스에 재할당한 것으로 감지됐다.

Script가 새 프로세스를 종료하지 않은 것은 안전한 동작이다.

## 8.3 성공 판정 기준

Stop 이후:

```text
PortForwardPid   :
PortForwardAlive : False
HostHealthUp     : False
```

동시에:

```text
MySqlReady    : True
BackendReady  : True
FrontendReady : True
```

따라서:

- Bridge 종료 성공
- 관계없는 프로세스 보호 성공
- Kubernetes Workload 유지
- PVC 유지

경고는 실패가 아니다.

---

# 9. 초기 PVC/MySQL Pending

## 9.1 증상

배포 직후:

```text
mysql-0 Pending
mysql-data-mysql-0 Pending
```

## 9.2 환경 특성

새 Docker Desktop kind Cluster가 시작된 직후 Local Path Provisioner와 StorageClass가 준비되는 데 시간이 걸릴 수 있다.

## 9.3 확인

```powershell
kubectl get storageclass
kubectl get pvc -n realtime-tile-game
kubectl describe pvc mysql-data-mysql-0 -n realtime-tile-game
kubectl get pods -n realtime-tile-game
```

최종적으로 MySQL이:

```text
mysql-0 1/1 Running
```

이 됐고 Backend InitContainer도 통과했다.

## 9.4 주의

초기 Pending만 보고 바로 다음을 하지 않는다.

- Cluster Reset
- Namespace 삭제
- PVC 삭제
- StatefulSet 전체 제거

먼저 Event와 Provisioner 상태를 확인한다.

---

# 10. 최종 재검증 절차

## 10.1 Kubernetes

```powershell
kubectl get pods,pvc,svc -n realtime-tile-game
```

목표:

```text
backend-*    1/1 Running
frontend-*   1/1 Running
mysql-0      1/1 Running
PVC          Bound
```

## 10.2 Self-test

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

목표:

```text
11 passed, 0 failed
```

## 10.3 방화벽

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

목표:

```text
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
```

## 10.4 Start

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.1.101
```

정상:

```text
Classroom LAN bridge started successfully.
Context : docker-desktop
Local   : http://127.0.0.1:30517
Client  : http://192.168.1.101:30517
CORS    : updated and backend rolled out
```

## 10.5 Test

```powershell
.\scripts\classroom-lan\Test-ClassroomLan.ps1
```

정상:

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

## 10.6 Stop

```powershell
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

정상:

```text
MySqlReady       : True
BackendReady     : True
FrontendReady    : True
PortForwardAlive : False
HostHealthUp     : False
```

---

# 11. 최종 판정

이번 집 환경 재검증으로 다음을 최종 확인했다.

```text
Docker Host 이미지 빌드                 PASS
kind Node containerd 이미지 Import      PASS
Backend Pod                             PASS
Frontend Pod                            PASS
MySQL Pod                               PASS
Self-test 11/11                         PASS
AccessDenied 판별                       PASS
PowerShell Parser 수정                  PASS
Firewall LocalSubnet 규칙               PASS
Classroom LAN Start                     PASS
CORS 갱신 및 Backend Rollout            PASS
localhost Health                        PASS
LAN Health                              PASS
Database                                PASS
Classroom LAN Stop                      PASS
Kubernetes Workload 유지                PASS
PVC 비파괴                              PASS
```

최종 상태:

```text
Phase 5.5-C Classroom LAN Deployment Readiness
FINAL PASS
```

---

# 12. 향후 개선 권장 사항

다음은 필수 장애 수정과 별개의 개선 후보다.

## 12.1 Local Image Load Script

예:

```text
scripts/k8s/Import-LocalImagesToDockerDesktopKind.ps1
```

역할:

- Node 컨테이너 자동 탐색
- Host Image 존재 확인
- `docker save`
- `docker cp`
- `ctr import`
- `crictl images` 검증
- 임시 tar 삭제

## 12.2 Script Parser Self-test

현재 기능 Mock 테스트 외에 모든 `.ps1`, `.psm1`을 PowerShell Parser로 읽어 Syntax Error를 사전에 검출한다.

## 12.3 인코딩 기준 문서화

- Script 파일 UTF-8 기준
- Windows PowerShell 5.1에서 재저장 주의
- 비ASCII Regex 최소화
- 자동 치환 시 원본 백업

## 12.4 운영 Runbook 보완

문서에 다음 분기 추가:

```text
ErrImageNeverPull
→ docker images 확인
→ Node crictl images 확인
→ Host/Node 저장소 분리 진단
→ kind load 또는 ctr import
```

이 개선 사항은 다음 Phase의 기능 범위와 섞지 말고, 인프라 문서 보완 또는 Phase 5.5 후속 정리로 분리하는 것이 안전하다.
