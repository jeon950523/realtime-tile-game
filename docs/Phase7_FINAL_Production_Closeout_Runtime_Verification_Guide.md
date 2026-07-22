# Phase 7 FINAL Production Closeout — Runtime Verification Guide

## 1. 자동검증

프로젝트 루트의 PowerShell에서 실행한다.

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\scripts\phase7\Verify-Phase7FinalCandidate.ps1
```

`frontend/node_modules`가 이미 정확한 lockfile 기준으로 설치된 경우:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\scripts\phase7\Verify-Phase7FinalCandidate.ps1 `
  -SkipInstall
```

기대 결과:

```text
Backend 전체 테스트 PASS
Frontend 31 files / 361 tests PASS
TypeScript PASS
Vite 156 modules Build PASS
Phase 7 FINAL Candidate 자동검증 PASS
```

## 2. Kubernetes 배포

프로젝트 루트에서 실행한다.

```powershell
docker build -t realtime-tile-game-backend:local .\backend
docker build -t realtime-tile-game-frontend:local .\frontend

kubectl config use-context docker-desktop
kubectl apply --dry-run=client -f .\k8s
kubectl apply -f .\k8s

kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout restart deployment/frontend -n realtime-tile-game

kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s
kubectl get pods,pvc -n realtime-tile-game
```

Health:

```powershell
curl.exe http://127.0.0.1:30517/api/health
```

기대:

```text
HTTP 200
Application UP
Database UP
backend/frontend/mysql Running
PVC Bound
```

## 3. 새 게임 생성

기존 활성 게임이 남아 있다면 한 계정에서 `게임 포기 및 나가기`를 실행한다.

2인 게임:

```text
포기자 FORFEITED
상대 WINNER
Game FINISHED
Room CLOSED
양쪽 activeRoom 해제
```

그 뒤:

```text
A 새 방 생성
B 입장
A/B READY
게임 시작
```

## 4. 첫 등록 전

현재 턴 계정에서 확인한다.

```text
현재 턴 표시가 명확함
상대 턴 계정은 Table 편집·Commit 불가
기존 Committed Meld 잠금
Rack Tile로 새 Meld 생성 가능
30점 미만 Commit 거부
Undo 정상
Cancel 시 Rack/Table Baseline 복구
```

정확히 30점 Fixture가 필요하면:

```text
scripts/phase7/phase7_exact30_runtime_fixture.sql
```

문서의 주의사항과 대상 game/user를 확인한 뒤 개발 DB에서만 사용한다.

## 5. 첫 등록 완료

30점 이상 Commit 후 확인한다.

```text
initialMeldCompleted = true
다음 턴 전환
모든 계정 공개 Table 동일
첫 등록 0/30 문구 없음
로컬 변경이 없을 때 이번 제출 0점 문구 없음
```

## 6. 기존 Meld 조작

첫 등록을 완료한 계정의 다음 턴에서 수행한다.

```text
기존 Meld 내부 순서 변경
기존 Meld 분리
두 Meld 병합
Table Tile을 다른 Meld로 이동
Rack Tile을 기존 Meld 앞에 추가
Rack Tile을 기존 Meld 중간에 추가
Rack Tile을 기존 Meld 뒤에 추가
```

확인:

```text
Drag 중 상대 화면은 변경되지 않음
유효하지 않은 중간 상태는 로컬에서 허용
유효하지 않은 상태에서는 Commit 불가
유효한 전체 Candidate Table만 Commit 성공
타일 중복 0
타일 소실 0
```

## 7. 혼잡 Table

Meld 수가 늘어난 상태에서 확인한다.

```text
Meld 사이 최소 1 Cell 간격
Overlap 0
충돌 Drop 시 오른쪽으로 자동 Nudge
오른쪽 공간 부족 시 다음 행 Wrap
화면은 8행 높이 유지
Table 내부 세로 Scroll
하단 Bottom Drop Row 접근 가능
하단 Edge Drag 시 Auto-scroll
Rack이 화면 아래로 밀리지 않음
```

## 8. Undo / Cancel

```text
여러 번 편집
→ Undo를 역순으로 반복
→ 각 단계 정상 복구

다시 여러 번 편집
→ Cancel
→ 턴 시작 시 Rack/Table 완전 복구
```

확인:

```text
중복 Tile 0
소실 Tile 0
Rack 수 정상
Meld 순서 정상
```

## 9. Refresh / Reconnect

Commit 전 로컬 편집 중 새로고침:

```text
미확정 로컬 변경 폐기
서버 마지막 확정 Table 복구
중복 구독 없음
중복 Commit 없음
```

Commit 후 새로고침:

```text
확정 Table 유지
Rack 유지
현재 턴 유지
상대 계정과 같은 공개 상태
```

로그아웃은 게임 포기와 다르다.

```text
로그아웃
→ activeRoom 유지
→ 재로그인 시 기존 게임 복귀
```

## 10. Console 최종 Gate

두 브라우저 모두 확인한다.

```text
Console Error 0
Console Warning 0
Unhandled Promise Rejection 0
WebSocket Subscription 중복 0
```

## 11. FINAL 판정

아래가 모두 통과하면 Phase 7 FINAL로 확정한다.

```text
자동검증 PASS
2계정 첫 등록 전/후 PASS
기존 Meld 편집 PASS
Rack → 기존 Meld PASS
혼잡 Table Scroll/Auto-scroll PASS
Undo/Cancel PASS
Refresh/Reconnect PASS
게임 포기 후 새 게임 PASS
Console 0
```
