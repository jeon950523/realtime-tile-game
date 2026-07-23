# Phase 7 Frontend Legacy Cleanup Runtime 검증표

## 자동 검증

```powershell
cd .\frontend; npm run check; cd ..
```

기대 결과:

```text
TypeScript PASS
Test Files 전부 PASS
Tests 전부 PASS
Production Build PASS
```

## Runtime 1 — 직접 연속 배치

```text
7 → 8 → 9를 같은 행 연속 Cell에 배치
기대: Candidate 1개, RUN, 중간 빈 Cell 없음
```

## Runtime 2 — 기존 Candidate 확장

```text
789 왼쪽에 6
기대: 6789, 기존 789 좌표 유지

789 오른쪽에 10
기대: 78910, 기존 789 좌표 유지
```

## Runtime 3 — 충돌 보정

```text
점유 Cell 위에 Tile 또는 Meld Drop
기대: 기존 내용은 움직이지 않고 이동 대상만 오른쪽 Gutter-safe 위치로 보정
행 끝이면 다음 행 0열부터 탐색
```

## Runtime 4 — Commit 위치 보존

```text
Table 중간 위치에 신규 Meld 배치
Commit
상대 PC와 다음 Turn 확인
기대: 서버 확정 좌표 유지, 왼쪽 위로 이동하지 않음
```

## Runtime 5 — Undo / Cancel

```text
Meld 이동 → Undo
기대: 직전 Working 좌표

Meld 이동 → Cancel
기대: 서버 확정 Baseline 좌표
```

## Runtime 6 — 첫 등록 정책

```text
첫 등록 전
기대: 기존 확정 Table 수정 불가, Rack 신규 Meld만 조작

30점 이상 Commit 후 다음 내 Turn
기대: 첫 등록 완료 표시, 기존 확정 Meld 수정 가능
```

## Runtime 7 — 2PC 동기화

```text
Host PC에서 이동
Client PC Preview 확인
Commit
양쪽 화면 확인
F5 / Reconnect 확인
```

기대:

- Preview 좌표 동일
- Commit 좌표 동일
- Refresh 후 좌표 동일
- Console Error 0

## 배포 후 상태

```powershell
kubectl get pods -n realtime-tile-game -o wide
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
```

정상 기준:

```text
FrontendReady True
PortForwardAlive True
HostHealthUp True
```
