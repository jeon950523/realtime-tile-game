# Frontend 전체 감사 결과

## 감사 범위

기준 파일: `frontend0723-10-18.zip`

정적 스캔 범위:

```text
TS/Vue 파일 91개
총 약 15,509줄
Phase 7 테스트 파일 포함 전체 Frontend Tree
```

집중 수동 검토 경로:

```text
GameView
→ WorkingTableBoard
→ useWorkingTable
→ tableFlow
→ tableCandidateDerivation
→ turnDraftValidation
→ gameStore.commitTurn
→ TurnPreviewBoard
→ CommittedTableBoard
```

## 감사 결론

### Blocking

현재 남은 1개 실패는 런타임 코드 오류가 아니라 이전 테스트 패치가
`FE-P7B-004`의 기대값까지 `13`으로 바꾼 문제다.

```text
FE-P7B-004 충돌 자동 보정: 4가 맞음
FE-P7B-009 Cancel 원본 복구: 13이 맞음
```

### 런타임 핵심 계약 확인

다음 구현은 현재 코드상 일치한다.

```text
직접 빈칸 드롭 → 정확한 좌표 유지
충돌 드롭 → Gutter 기반 오른쪽 이동 / 다음 행 Wrap
확정 Meld Baseline → 서버 gridRow/gridColumn 유지
Committed Board → 서버 좌표 렌더
Turn Preview → Preview 좌표 렌더
첫 등록 전 → 기존 확정 Meld 잠금
첫 등록 완료 후 → 기존 확정 Meld 편집 허용
Undo → 직전 Working Snapshot 복구
Cancel → 서버 확정 Baseline 복구
Commit → Tile 단위 gridRow/gridColumn 전송
```

## 비차단 위험

### 1. `flowCommittedTableMelds()` 잔존

운영 호출은 없고 테스트에서만 사용하지만, 함수 의미가 현재의 서버 확정 좌표 보존 계약과 반대다.
나중에 운영 코드에서 다시 호출되면 Meld가 왼쪽 위로 재배치되는 버그가 재발할 수 있다.

판단:

```text
이번 Hotfix에서는 유지
Phase 7 종료 후 Legacy Cleanup에서 제거 또는 명시적 AutoLayout 전용 이름으로 변경
```

### 2. `flowWorkingPlacements()` 미사용

현재 Frontend 전체에서 호출되지 않는 Dead Export다.

판단:

```text
기능 영향 없음
후속 Cleanup 대상
```

### 3. Legacy Commit 입력 경로

`gameStore.commitTurn()`은 과거 Meld 단위 입력도 지원하고,
좌표 미지정 시 `meldIndex * 13`을 기본 Column으로 사용한다.
3개 이상 Meld에는 18열 범위를 벗어날 수 있다.

현재 Production `GameView`는 Tile Placement를 넘기므로 실제 경로에서는 사용하지 않는다.

판단:

```text
현재 MVP Blocking 아님
Legacy 테스트 정리 후 제거 권장
```

### 4. `WorkingTableBoard`의 Legacy `melds` Prop

Production은 `placements`를 넘기지만 일부 Regression Test가 과거 Meld View를 사용한다.
두 입력 경로가 계속 공존하면 테스트와 Production 계약이 다시 갈라질 수 있다.

판단:

```text
Phase 7 종료 후 모든 테스트를 placements 기준으로 통일
legacy prop 제거 권장
```

### 5. Drag Occupied Cell Cache가 판정에 직접 사용되지 않음

`occupiedCellsDuringDrag`를 만들지만 실제 배치 판정은 `canPlaceTableTiles()`가 다시 Set을 생성한다.
정확성 문제는 아니고 드래그 성능 최적화가 완성되지 않은 상태다.

판단:

```text
현재 성능 테스트 통과 범위에서는 유지 가능
2차 성능 개선 대상
```

### 6. `MELDS_COMMITTED` 후 Table 갱신 의존성

`MeldsCommittedPayload`에는 전체 Table이 없으므로 Frontend는 이후 도착하는
Authoritative `GAME_STATE_UPDATED`를 통해 실제 Table을 갱신하는 구조로 보인다.

Frontend 파일만으로 Backend Event 순서를 확정할 수 없으므로 버그로 단정할 수 없다.

판단:

```text
Runtime에서 Commit 직후 상대 화면의 Table이 잠깐 이전 상태로 보이는지 확인
문제가 있으면 Backend Event 계약까지 같이 감사
```

## 지금 당장 고칠 것

```text
FE-P7B-004 기대값 4 복구
FE-P7B-009 기대값 13 유지
6 + 789 / 789 + 10 회귀 테스트 추가
전체 npm run check 통과 확인
Runtime 2PC 검증
```

## 나중에 미뤄도 되는 것

```text
Dead Flow 함수 제거
Legacy Commit 입력 제거
Legacy melds Prop 제거
Drag Cache 실제 적용
테스트 파일 통합 및 이름 정리
```
