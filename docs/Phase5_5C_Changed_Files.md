# Phase 5.5-C 변경 파일

작성 기준: 2026-07-16 KST

유일한 기준 전체본:

```text
phase0716-1511-phase5_5B-final-clean-source.zip
SHA-256 f205c53d111a845443ea3ccf3fdd5d4f2537eea10ab310dbfc221c629443d71c
```

## 수정 파일

```text
.gitignore
README.md
k8s/README.md
```

변경 목적:

```text
.runtime/ 산출물 제외
Classroom LAN 실행 방식 추가
Host/Client 검증·보안 경계·운영 절차 추가
```

## 신규 운영 코드

```text
scripts/classroom-lan/ClassroomLan.Common.psm1
scripts/classroom-lan/Start-ClassroomLan.ps1
scripts/classroom-lan/Stop-ClassroomLan.ps1
scripts/classroom-lan/Get-ClassroomLanStatus.ps1
scripts/classroom-lan/Test-ClassroomLan.ps1
scripts/classroom-lan/Enable-ClassroomLanFirewall.ps1
scripts/classroom-lan/Disable-ClassroomLanFirewall.ps1
scripts/classroom-lan/Invoke-ClassroomLanSelfTest.ps1
scripts/classroom-lan/README.md
```

## 신규 문서

```text
docs/Phase5_5C_Changed_Files.md
docs/Phase5_5C_Completion_Report.md
docs/Phase5_5C_Direct_Verification_Guide.md
docs/Phase5_5C_Classroom_LAN_Architecture_And_Operations.md
docs/Realtime_Tile_Game_Phase5_5A_5B_Troubleshooting_Guide.md
```

## 1차 검수 보완

```text
scripts/classroom-lan/Invoke-ClassroomLanSelfTest.ps1
README.md
docs/Phase5_5C_Changed_Files.md
docs/Realtime_Tile_Game_Phase5_5A_5B_Troubleshooting_Guide.md
```

보완 내용:

```text
Status View Self-test Mock State에 Port 30517 추가
Phase 5.5-A·5.5-B Runtime 트러블슈팅 전용 문서 추가
README에 트러블슈팅 문서 경로와 범위 연결
문서 산출물 목록 갱신
```

## 변경하지 않은 영역

```text
Backend Java Production/Test
Frontend Production/Test
Flyway·DB Schema
Dockerfile·Compose
Kubernetes Manifest
게임 규칙과 Phase 6 기능
```

실제 LAN IP, Secret, `.env`, Runtime PID·Log는 저장소와 산출물에 포함하지 않는다.


## Runtime 검수 Firewall 권한 분류 보완

수정 운영 코드:

```text
scripts/classroom-lan/ClassroomLan.Common.psm1
scripts/classroom-lan/Start-ClassroomLan.ps1
scripts/classroom-lan/Get-ClassroomLanStatus.ps1
scripts/classroom-lan/Test-ClassroomLan.ps1
scripts/classroom-lan/Invoke-ClassroomLanSelfTest.ps1
```

수정 문서:

```text
README.md
scripts/classroom-lan/README.md
docs/Phase5_5C_Changed_Files.md
docs/Phase5_5C_Completion_Report.md
docs/Phase5_5C_Direct_Verification_Guide.md
docs/Phase5_5C_Classroom_LAN_Architecture_And_Operations.md
docs/Realtime_Tile_Game_Phase5_5A_5B_Troubleshooting_Guide.md
```

보완 내용:

```text
Get-NetFirewallRule·Filter 조회에 terminating error 적용
AccessDenied / NotFound / ConfigurationMismatch / Match 분리
AccessDenied에서 QuerySucceeded false, Exists null 유지
Start·Test 관리자 실행 안내 정확화
Status의 FirewallRuleExists 빈 값 출력
AccessDenied 자동 Self-test Case 추가
실제 Host/Client Runtime 통과 기록 반영
```

변경하지 않은 영역:

```text
Kubernetes Manifest
Backend·Frontend Production Code
게임 기능
DB Schema
Port-forward Bind·CORS·PID 관리 Architecture
```
