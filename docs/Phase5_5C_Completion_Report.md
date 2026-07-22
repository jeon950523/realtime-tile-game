# Phase 5.5-C 구현 및 Runtime 검수 보고서

작성 기준: 2026-07-16 KST

## 1. 현재 판정

```text
Phase 5.5-C Architecture 구현 완료
실제 Windows Host Runtime 검증 완료
실제 내부망 Client PC 검증 완료
Firewall AccessDenied 오분류 보완 적용
보완 Self-test 11/11 통과
관리자 Start·Status·Test 통과
Stop 이후 Kubernetes Workload/PVC 유지 확인
Phase 5.5-C FINAL PASS
```

이번 Runtime 보완은 Firewall Rule 조회 결과의 오류 분류와 안내만 변경했다. 게임, Kubernetes Workload, Port-forward Architecture, CORS 갱신 방식은 변경하지 않았다.

## 2. 실제 Runtime 통과 결과

사용자 Windows Host와 같은 내부망 Client PC에서 다음 항목이 실제 통과했다.

```text
PowerShell Self-test 11/11
Firewall Rule 생성 성공
Firewall Rule 중복 실행 안전
Classroom LAN Start 성공
kubectl context docker-desktop 확인
127.0.0.1 + 선택 LAN IPv4 Bind 성공
정확한 CORS Origin 갱신
Backend Recreate Rollout 성공
Status Script 정상
Test Script 정상
localhost Health UP
LAN IPv4 Health UP
Database UP
중복 Start 시 기존 PID 유지
Stop 시 Port-forward만 종료
Kubernetes Workload/PVC 유지
Stop 후 LAN URL 연결 실패
재시작 시 새 PID로 정상 기동
다른 PC에서 접속 성공
다른 PC에서 방 생성·게임 시작 정상
로그아웃 후 재접속·새로고침·게임 회귀 정상
```

전체 Client 게임 회귀는 Firewall 오류 분류와 무관하므로 이번 보완 후 반복하지 않아도 된다.

## 3. Runtime에서 발견한 결함

일반 권한 PowerShell에서 `Get-NetFirewallRule`이 다음 오류를 반환할 수 있었다.

```text
PermissionDenied
Windows System Error 5
```

기존 구현은 `-ErrorAction SilentlyContinue`로 오류를 숨겨 정상 Rule이 존재해도 다음처럼 오판했다.

```text
Exists False
Reason NotFound
```

이는 Rule 누락이 아니라 조회 권한 부족이다.

## 4. 보완 구현

### Firewall Query 상태 모델

```text
정상 조회 + Rule 없음
→ QuerySucceeded True
→ Exists False
→ IsValid False
→ Reason NotFound

정상 조회 + 설정 불일치
→ QuerySucceeded True
→ Exists True
→ IsValid False
→ Reason ConfigurationMismatch

정상 조회 + 일치
→ QuerySucceeded True
→ Exists True
→ IsValid True
→ Reason Match

권한 거부
→ QuerySucceeded False
→ Exists null
→ IsValid False
→ Reason AccessDenied
```

`Get-NetFirewallRule`, `Get-NetFirewallPortFilter`, `Get-NetFirewallAddressFilter`는 모두 terminating error 경계에서 처리한다. 알 수 없는 조회 실패는 `QueryFailed`이며 `NotFound`로 축소하지 않는다.

### Start 안내

`AccessDenied`이면 다음 의미로 명확하게 실패한다.

```text
Firewall verification requires an elevated PowerShell.
Run Start-ClassroomLan.ps1 as administrator.
```

`NotFound`와 `ConfigurationMismatch`는 각각 별도 메시지를 유지한다.

### Status 출력

조회 권한이 없어 존재 여부를 알 수 없을 때 `False`로 단정하지 않는다.

```text
FirewallRuleExists :
FirewallRuleValid  : False
FirewallReason     : AccessDenied
```

## 5. 보안·Architecture 불변

```text
RFC1918 LAN IPv4만 허용
복수 Adapter 후보 시 명시 선택
127.0.0.1 + 선택 LAN IPv4만 Bind
0.0.0.0 미사용
CORS Origin 정확한 세 개로 교체
ConfigMap 변경 시 Backend Recreate Rollout
PID + Process Start Time + Command Line 검증
무관 Process 종료 금지
Firewall TCP 30517 + LocalSubnet
.runtime Git·Patch 제외
Backend/MySQL 직접 외부 노출 없음
Backend replicas 1
MySQL StatefulSet + PVC 유지
```

## 6. 기존 자동 검증

```text
Frontend npm ci 통과
Frontend Vitest 99/99 통과
TypeScript 통과
Production Build 통과
npm audit 취약점 0
```

Backend 코드는 Phase 5.5-C에서 변경하지 않았다. 구현 환경에서는 Maven Wrapper 배포본 다운로드 DNS 오류로 Backend Test가 시작되지 않았으며, Phase 5.5-B FINAL Runtime 검증 결과를 유지한다.

## 7. 보완 Self-test 및 최종 재검증

기존 10개 Case에 다음 Case를 추가했다.

```text
Firewall permission denial is reported as AccessDenied, not NotFound
```

최종 실행 결과:

```text
Classroom LAN self-test result: 11 passed, 0 failed
FirewallRuleExists : True
FirewallRuleValid  : True
FirewallReason     : Match
LocalHealth        : UP
LanHealth          : UP
Database           : UP
```

Stop 이후에도 다음 상태를 확인했다.

```text
MySqlReady       : True
BackendReady     : True
FrontendReady    : True
PortForwardAlive : False
HostHealthUp     : False
```

따라서 Classroom LAN Bridge만 종료되고 Kubernetes Workload와 PVC는 유지된다.

## 8. 최종 판정

```text
Self-test 11/11                         PASS
관리자 PowerShell Start                 PASS
관리자 Status Firewall Match            PASS
관리자 Test                              PASS
localhost Health                         PASS
LAN Health                               PASS
Database                                 PASS
다른 PC 접속·방 생성·게임 시작          PASS
로그아웃·재접속·새로고침·게임 회귀      PASS
Stop 이후 Port-forward 종료              PASS
Kubernetes Workload/PVC 유지             PASS

Phase 5.5-C Classroom LAN Deployment Readiness
FINAL PASS
```
