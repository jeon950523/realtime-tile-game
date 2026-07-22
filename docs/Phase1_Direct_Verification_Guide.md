# Phase 1 직접 검증 가이드

작성 기준: 2026-07-15  
기준 전체본: `phase0715-00-39.zip`

## 1. 검증 목적

이번 Phase는 신규 화면이나 API가 아니라 순수 Java 타일 도메인과 규칙 엔진을 추가한다. 완료 판정의 중심은 브라우저가 아니라 자동 테스트와 변경 범위 확인이다.

## 2. 사전 조건

```text
Java 17
프로젝트 Maven Wrapper 사용 가능
최신 Phase 0 전체본에 patch 적용 완료
```

Java 확인:

```powershell
java -version
.\backend\mvnw.cmd -version
```

`java.version`은 17이어야 한다.

## 3. 전체 Backend clean test

프로젝트 루트 기준:

```powershell
cd .\backend
.\mvnw.cmd clean test
```

정상 기준:

```text
BUILD SUCCESS
Tests run: 112
Failures: 0
Errors: 0
Skipped: 0
```

최종 수치는 `target/surefire-reports`에서도 확인한다.

```powershell
Get-ChildItem .\target\surefire-reports\*.txt |
Select-String -Pattern "Tests run:"
```

## 4. Phase 1 순수 도메인 테스트만 실행

```powershell
.\mvnw.cmd `
  -Dtest="com.realtimetilegame.game.domain.**" `
  test
```

정상 기준:

```text
Phase 1 순수 도메인 테스트 108개 통과
Failures 0
Errors 0
```

환경에 따라 Surefire의 package wildcard가 다르게 처리되면 전체 `clean test` 결과를 기준으로 하거나 테스트 클래스명을 명시한다.

## 5. 프레임워크 의존성 차단 확인

```powershell
Get-ChildItem `
  .\src\main\java\com\realtimetilegame\game\domain `
  -Recurse `
  -Filter *.java |
Select-String `
  -Pattern "org\.springframework|jakarta\.persistence|jakarta\.validation|com\.fasterxml"
```

정상 결과:

```text
출력 없음
```

## 6. 테스트 ID 추적 확인

다음 문서를 연다.

```text
docs/Phase1_Test_Case_Traceability.md
```

아래 ID가 모두 존재하는지 확인한다.

```text
GAME-001~007
RUN-001~013
GROUP-001~010
INIT-001~011
TURN-001~009
JOKER-001~011
```

PowerShell 확인 예:

```powershell
$trace = Get-Content .\docs\Phase1_Test_Case_Traceability.md -Raw

1..7  | ForEach-Object { "GAME-{0:D3}"  -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
1..13 | ForEach-Object { "RUN-{0:D3}"   -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
1..10 | ForEach-Object { "GROUP-{0:D3}" -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
1..11 | ForEach-Object { "INIT-{0:D3}"  -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
1..9  | ForEach-Object { "TURN-{0:D3}"  -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
1..11 | ForEach-Object { "JOKER-{0:D3}" -f $_ } | ForEach-Object { if ($trace -notmatch $_) { $_ } }
```

정상 결과는 출력 없음이다.

## 7. 변경 범위 확인

Patch 안에는 다음만 있어야 한다.

```text
README.md
backend/src/main/java/com/realtimetilegame/game/domain/**
backend/src/test/java/com/realtimetilegame/game/**
docs/Phase1_Changed_Files.md
docs/Phase1_Test_Case_Traceability.md
docs/Phase1_Completion_Report.md
docs/Phase1_Direct_Verification_Guide.md
```

다음 파일·디렉터리가 포함되면 안 된다.

```text
.env
.env.local
backend/target
frontend/node_modules
frontend/dist
*.tsbuildinfo
IDE 설정
로컬 로그
```

또한 다음 영역의 변경이 없어야 한다.

```text
backend/pom.xml
backend/src/main/resources
기존 Controller
기존 WebSocket endpoint와 Handler
frontend
frontend/package-lock.json
Flyway Migration
```


## 7-1. 최종 검수 보완 회귀 확인

다음 테스트 메서드가 모두 PASS인지 확인한다.

```text
sameMeldIdCannotBypassJokerReplacement
sameMeldIdRoleChangeRequiresReplacement
sameMeldIdContextChangeRequiresSameTurnReuse
sameMeldIdWithUnchangedJokerRoleIsNotFalsePositive
renamingMeldIdAloneCannotChangeRuleOutcome
sameRoleWithChangedJokerIndexRequiresReplacement
jokerAssignmentOrderFollowsCandidateJokerOrder
initialMeldRackContributionOrderFollowsTurnStartRackOrder
repeatedValidationReturnsSameOrderedCollections
rejectsNullParticipant
initialDistributionRejectsNullParticipantKey
initialDistributionRejectsNullRack
```

Java 17 환경에서 다음 결과가 확인되기 전에는 Phase 1을 최종 완료 처리하지 않는다.

```text
BUILD SUCCESS
Tests run: 112
Failures: 0
Errors: 0
Skipped: 0
```

## 8. 대표 규칙 결과 확인

자동 테스트 이름으로 다음을 확인한다.

```text
RUN 입력 순서 보존
J,J,J RUN 실패
J,J,J GROUP 실패
CLASSIC 첫 등록 30점
SPEED 첫 등록 생략
기존 테이블 타일 손패 이동 차단
공용 풀 타일 직접 사용 차단
조커 회수 후 같은 턴 재사용
전체 106개 중복·유실 검증
검증 실패 후 입력 상태 불변
Deadlock 단독 승자와 DRAW
SPEED 단독 승자와 DRAW
```

## 9. 선택적 Phase 0 실행 회귀

신규 규칙 엔진은 REST/WebSocket과 연결되지 않았으므로 필수 화면 검증은 없다. 필요하면 기존 Phase 0 기반을 선택적으로 확인한다.

```text
Spring Boot 실행
GET /api/health 성공
Database = UP
기존 WebSocket 상태 화면 최초 연결
기존 수동 재연결
```

이 검증은 Phase 1 신규 규칙의 완료 근거가 아니라 Phase 0 실행 환경 보존 확인이다.

## 10. 통과 보고 형식

```text
Java 버전:
Maven clean test:
전체 Tests run:
Failures:
Errors:
Skipped:
Phase 1 순수 테스트:
HealthApiIntegrationTest:
WebSocketHealthIntegrationTest:
프레임워크 의존성 검색 결과:
Patch 범위 이상 여부:
최종 판정:
```
