# Phase 7 Runtime Fixture Guide

## 용도

무작위 초기 분배와 무관하게 첫 등록 정확히 30점을 재현하기 위한 로컬 전용 픽스처다. 운영 데이터에는 사용하지 않는다.

픽스처 파일:

```text
scripts/phase7/phase7_exact30_runtime_fixture.sql
```

대상 계정 이메일은 스크립트의 `phase7.runtime.b.20260718.0312@example.test`이며, 최신 `IN_PROGRESS` 게임을 대상으로 한다. 필요하면 로컬 테스트 계정 이메일만 수정한다.

## 사전 조건

1. Compose MySQL·Backend·Frontend가 실행 중이다.
2. 2인 방을 시작해 `IN_PROGRESS` Game이 존재한다.
3. 대상 계정이 해당 게임 참가자다.

Compose 기본 포트:

```text
Frontend  http://127.0.0.1:5173
Backend   http://127.0.0.1:8080
MySQL     127.0.0.1:33307
```

`33307`은 브라우저 프론트 포트가 아니라 MySQL host port다.

## 실행

PowerShell에서 `.env`의 DB 값을 읽어 MySQL 컨테이너 표준 입력으로 전달한다. 비밀번호를 문서나 ZIP에 기록하지 않는다.

```powershell
$phase7Env = @{}
Get-Content -Encoding UTF8 .env | ForEach-Object {
  if ($_ -match '^([^#=]+)=(.*)$') { $phase7Env[$matches[1]] = $matches[2] }
}
$phase7DbUser = $phase7Env['MYSQL_USER']
$phase7DbPassword = $phase7Env['MYSQL_PASSWORD']
$phase7DbName = $phase7Env['MYSQL_DATABASE']

Get-Content -Raw -Encoding UTF8 scripts/phase7/phase7_exact30_runtime_fixture.sql |
  docker compose exec -T mysql mysql `
    --user=$phase7DbUser `
    --password=$phase7DbPassword `
    $phase7DbName --batch --raw
```

## 기대 Rack

```text
RED-07-A, RED-08-A, RED-09-A   = RUN 24점
BLUE-01-A, BLUE-02-A, BLUE-03-A = RUN 6점
기타 filler 8장
```

스크립트는 대상 플레이어 Rack을 14장으로 만들고, 나머지 Rack도 연속 `position_order`로 다시 정렬하며, 전체 타일의 위치·Owner·Meld 링크 불변조건을 보존한다.

## 브라우저 절차

1. `789` 정렬을 누른다.
2. RED 7을 Hold해 RED 789 그룹을 TurnDraft에 놓는다.
3. BLUE 1을 Hold해 BLUE 123 그룹을 새 Draft Meld로 놓는다.
4. `첫 등록 30/30`, RUN 24점, RUN 6점, COMMIT 버튼 활성화를 확인한다.
5. COMMIT 후 Rack 8장, Table 2 Meld, 다음 플레이어 턴을 확인한다.
6. 새로고침하고 다른 계정으로 로그인해 같은 Table을 확인한다.

