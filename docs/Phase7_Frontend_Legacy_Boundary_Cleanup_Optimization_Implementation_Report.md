# Phase 7 Frontend Legacy Boundary Cleanup + Optimization 구현 보고서

## 1. 기능 목적

Phase 7 Table Runtime에서 좌표의 유일한 원본을 Tile Placement로 통일한다.
구형 Meld 배열이나 Frontend 자동 Flow가 서버 확정 좌표를 다시 계산하지 못하게 하고,
Drag 중 반복 계산을 줄이되 직접 배치와 충돌 보정 규칙은 유지한다.

## 2. 전체 호출 순서

```text
Rack Drag / Table Drag
→ WorkingTableBoard.resolveRackDropTarget 또는 calculateTableDropPreview
→ Drag 시작 시 createTableCoordinateResolver 1회 생성
→ Pointer Frame마다 같은 Occupancy Snapshot으로 resolveInteractive
→ 빈 Cell이면 요청 좌표 그대로 유지
→ 점유 Cell이면 Gutter-aware resolveNearest
→ useWorkingTable Mutation
→ WorkingTilePlacement[] 갱신
→ deriveTableCandidates로 Candidate 파생
→ validateTurnDraft
→ gameStore.commitTurn(WorkingTilePlacement[])
→ Store의 중복 Tile / 중복 Cell / 18×18 범위 검사
→ STOMP tilePlacements 전송
→ 서버 확정 Snapshot 수신
→ committedMeldsToPlacements가 서버 좌표 그대로 Baseline 생성
→ WorkingTableBoard / CommittedTableBoard / TurnPreviewBoard 렌더
```

## 3. 변경 파일 역할

### Runtime

- `src/domain/game/tableFlow.ts`
  - 구형 Full Flow API 제거
  - Occupancy Snapshot 기반 `TableCoordinateResolver` 추가
  - 직접 배치와 Gutter Fallback 계약 유지

- `src/domain/game/tableGrid.ts`
  - 구형 Meld 기반 Grid API 제거
  - 미사용 Table 자동 빈 좌표 API 및 미사용 Compact 상수 제거

- `src/domain/game/tableCandidateDerivation.ts`
  - Source Meld 원본 Tile Set을 한 번만 만들어 Candidate마다 전체 Placement를 다시 Filter하지 않음

- `src/domain/game/turnDraftValidation.ts`
  - 첫 등록 전 기존 Table Lock의 Meld 순서 기반 Legacy Fallback 제거
  - 기존 Table이 있다면 Tile Placement 좌표가 반드시 서버 Baseline과 일치해야 함

- `src/composables/game/useWorkingTable.ts`
  - WorkingTableState의 파생 `melds` 호환 Getter 제거
  - Candidate는 `candidates` Computed에서만 파생
  - Mutation 불변식 검사에서 Tile ID Set 재사용

- `src/components/game/WorkingTableBoard.vue`
  - `placements` 필수 Prop 하나로 렌더 계약 통일
  - 구형 `melds` Prop과 변환 Fallback 제거
  - 내부/외부 Drag Resolver Cache 추가

- `src/stores/game.ts`
  - `commitTurn()`을 `CommitTilePlacementCommand[]` 전용으로 축소
  - `meldIndex * 13` 구형 좌표 생성 제거
  - Commit 직전 Layout 검증 추가

- `src/types/game.ts`, `src/types/turnDraft.ts`
  - 사용되지 않는 구형 Commit/Meld/TurnDraft 타입 제거

### Tests

- 기존 `workingTable.value.melds` 참조를 `candidates` 공식 파생 결과로 변경
- WorkingTableBoard Mount를 `placements` 기준으로 통일
- Commit Test를 Tile Placement 입력으로 통일
- Store Boundary Invalid Placement Test 추가
- Occupancy Resolver 재사용 Test 추가
- 첫 등록 Baseline Lock이 Placement 좌표를 요구하는 Test 추가

## 4. 제거한 위험

### 확정 좌표 재배치

```text
기존 위험: flowCommittedTableMelds → 왼쪽 위 자동 재배치 가능
현재: 함수 자체 제거, committedMeldsToPlacements만 사용
```

### Working Table 이중 원본

```text
기존 위험: placements와 melds Prop을 동시에 지원
현재: placements만 원본, Candidate는 항상 파생
```

### 구형 Commit 좌표 생성

```text
기존 위험: 좌표 없는 Meld 입력을 meldIndex * 13으로 변환
현재: Tile Placement만 허용, 범위와 중복을 Store에서 차단
```

### 첫 등록 Lock 우회

```text
기존 위험: Placement가 없으면 Meld 순서와 Tile 순서만으로 기존 Table Lock 판정
현재: 기존 Table이 있으면 서버 좌표와 Source Meld까지 일치하는 Placement가 필요
```

## 5. 최적화 내용

### Drag 좌표 검색

기존에는 Pointer 이동과 각 후보 Cell 검색 때마다 Placement를 Filter하고 Set을 다시 만들었다.
현재는 Drag 시작 시 점유 Cell Snapshot을 한 번 만들고 같은 Resolver를 재사용한다.

### Candidate 파생

기존에는 Candidate마다 Source Meld 원본 Tile을 찾기 위해 전체 Placement를 다시 Filter했다.
현재는 Source Meld별 Tile ID Set을 한 번 만든 뒤 조회한다.

### Mutation 불변식

기존 Baseline Tile 존재 여부 확인의 반복 `includes`를 Set 조회로 변경했다.

## 6. 판단

- 발표용: 좌표 원본과 Commit 입력을 하나로 통일했다고 설명 가능
- 1차 MVP: 위험한 Legacy 좌표 경로를 지금 제거하는 것이 맞음
- 실무 기준: Store와 Backend가 모두 Placement Layout을 검증하는 이중 방어가 필요함. Frontend 검사는 UX와 빠른 실패용이며 서버 검증을 대체하지 않음
- 2차 개선: `clientMeldId` 명칭과 Test-only `DraftMeld.vue`는 별도의 Terminology/UI Cleanup으로 분리하는 편이 안전함
