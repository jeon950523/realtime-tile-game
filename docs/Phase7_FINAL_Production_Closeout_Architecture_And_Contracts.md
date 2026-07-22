# Phase 7 FINAL Production Closeout — Architecture And Contracts

## 1. 권위 경계

```text
Server Committed Table
→ 공개 확정 상태의 원본

Client Working Table
→ 현재 턴 동안만 존재하는 로컬 편집본

Commit
→ 전체 Candidate Table을 서버에 전송
→ 서버 Rule Engine 검증
→ 한 Transaction으로 확정 Table 교체
```

Renderer와 Layout은 서버 게임 규칙의 권위를 대체하지 않는다.

## 2. Committed Full Flow

Committed Table 표시 순서는 다음 기준으로 고정한다.

```text
gridRow
→ gridColumn
→ meldId
```

이 순서로 Meld를 읽은 뒤 18열 안에 순차 배치한다.

```text
현재 행에 들어감
→ 현재 행 오른쪽에 1 Cell Gutter 포함 배치

현재 행에 들어가지 않음
→ 다음 행 0열부터 배치
```

Full Flow는 Presentation과 현재 턴의 Working Baseline에 동일하게 적용해, 화면과 편집 좌표가 어긋나지 않게 한다.

## 3. Working Local Reflow

### Drop 좌표 해결

사용자가 요청한 좌표가 충돌하면 다음 순서로 탐색한다.

```text
요청 행의 요청 열부터 오른쪽 탐색
→ 같은 행에 자리가 없으면 다음 행 0열부터 탐색
→ 18행을 넘으면 배치 실패
```

위쪽 또는 왼쪽으로 돌아가지 않으므로 반복 Drop에서 블록이 왕복하거나 예측 불가능한 위치로 이동하지 않는다.

### Meld 폭 변경

기존 Meld에 타일을 추가하거나 재정렬할 때:

```text
편집 대상 이전의 Meld: 고정
편집 대상 Meld: 기존 위치 유지 우선
편집 대상 이후 Meld: 충돌할 때만 오른쪽/다음 행으로 이동
```

이 계약으로 Rack Tile을 첫 번째 Meld에 추가해 폭이 커지는 경우 두 번째 Meld가 한 칸 밀리고, 대상 Meld가 화면의 다른 위치로 도망가지 않는다.

### Table Tile 이동

Table Tile을 다른 Meld에 넣을 때:

```text
Tile source = COMMITTED_TABLE 유지
sourceMeldId 유지
Target Meld 폭 증가
뒤쪽 Meld Local Reflow
```

Rack Tile로 잘못 변환하거나 원본 출처를 잃지 않는다.

## 4. Gutter 불변식

각 Meld Block은 같은 행에서 양옆으로 최소 1 Cell 간격을 요구한다.

```text
Block occupied cells
+ left gutter 1
+ right gutter 1
```

Board 바깥쪽 Gutter는 요구하지 않는다. 따라서 0열에서 시작하거나 17열에서 끝나는 Block은 허용된다.

## 5. Scroll와 좌표 변환

```text
Logical Canvas: 최대 18행
Viewport: 8행 고정
Content Rows: occupied max row + Bottom Drop 1행
최소 Content Rows: 8
최대 Content Rows: 18
```

Pointer 좌표는 Viewport 높이가 아니라 실제 Canvas Content Row 수를 기준으로 Grid Row로 변환한다.

Auto-scroll은 다음 조건에서만 RAF를 시작한다.

```text
실제 Viewport 높이 > 0
scrollHeight > clientHeight
활성 Table Drag 또는 외부 Rack Drag 존재
Pointer가 상단/하단 Edge Threshold 안에 있음
```

Scroll이 필요 없는 Board에서는 Preview RAF 외에 불필요한 두 번째 RAF를 만들지 않는다.

## 6. Undo / Cancel / Reconnect

### Undo

최근 로컬 Transaction 100개만 유지한다.

```text
101번째 기록 발생
→ 가장 오래된 Snapshot 제거
→ 최근 100개 유지
```

### Cancel

```text
Working Placements → Turn 시작 Baseline
History → []
Rack Display Order → Baseline Source Order
```

### Stale / Reconnect

서버 Version 또는 확정 Table Fingerprint가 바뀌면 로컬 상태를 그대로 Commit하지 않는다.

```text
변경 없음
→ 새 Authoritative Snapshot으로 Working Table 재생성

로컬 변경 있음
→ STALE 처리
→ 기존 Commit 권한 차단
```

## 7. Initial Meld 권한

```text
첫 등록 전
→ CURRENT_PLAYER_RACK Tile로 만든 새 Meld만 편집 가능
→ 기존 COMMITTED_TABLE Meld 잠금

첫 등록 후
→ 기존 Meld 수정·분리·병합 가능
→ Rack Tile을 기존 Meld에 추가 가능
```

Commit 유효성은 기존 Server Rule Engine이 최종 판단한다.
