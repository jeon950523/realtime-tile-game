# Phase 7 FINAL Production Closeout — 구현 요약

작성일: 2026-07-20  
상태: **FINAL Candidate**  
기준 전체본: `phase0719-22-12-phase7-precloseout-clean-source.zip`

## 1. 최종 Renderer 결정

Production Renderer는 기존 **HTML5 Drag + Vue DOM**을 유지한다.

```text
HTML5 DOM: Production 유지
Native Pointer DOM: Production 전환 중단
Konva: Production 후보 제외
추가 Renderer Spike: 진행하지 않음
```

Native Pointer Spike에서 Long Task를 제거한 뒤에도 Main Task p95가 HTML5보다 높았으므로, 안정적으로 동작하던 HTML5를 교체할 근거가 부족하다고 판단했다.

이번 Closeout에는 Native Pointer/Konva 코드가 들어가지 않았다.

## 2. Table Layout 최종 정책

### Committed Table

서버가 확정한 Meld는 화면 표시 시 **Full Flow**로 정렬한다.

```text
서버 Meld 순서 유지
왼쪽 → 오른쪽 배치
Meld 사이 1 Cell Gutter
오른쪽 공간 부족 시 다음 행
```

서버의 과거 좌표가 흩어져 있거나 서로 너무 가까워도, 화면에서는 서로 다른 Meld가 붙어 보이지 않는다.

### Working Table

현재 턴 사용자가 편집하는 Table은 **Local Reflow**를 사용한다.

```text
편집 대상 Meld는 현재 위치 유지 우선
폭이 늘어나 충돌하면 뒤쪽 Meld만 오른쪽으로 밀기
오른쪽 공간 부족 시 다음 행으로 Wrap
위쪽 또는 왼쪽으로 역방향 점프하지 않음
```

Rack Tile을 기존 Meld에 추가하거나 Table Tile을 다른 Meld로 이동할 때도, 타일 출처와 원본 Meld 메타데이터를 보존한다.

## 3. Table 크기와 Scroll

```text
열: 18
논리 행: 18
화면에 보이는 행: 8
Meld Gutter: 1 Cell
하단 Drop Row: 1행
Undo History: 최근 100회
```

화면 전체 높이를 계속 늘리지 않고 Table Viewport 내부에서 세로 Scroll한다. 따라서 Table이 혼잡해져도 Rack이 문서 아래로 밀리지 않는다.

하단에 새 Meld를 놓을 수 있는 Drop Row를 유지하며, Rack 또는 Table Drag가 Scroll 경계에 머물면 내부 Auto-scroll이 동작한다.

## 4. Turn UX 마감

- 내 턴일 때 Game Board에 강한 현재 턴 표시 적용
- 상대 턴에는 현재 턴 강조 제거
- 첫 등록 전에는 기존 Committed Meld 편집 금지 유지
- 첫 등록 완료 후 기존 Meld 편집·분리·병합 허용
- Rack Tile을 기존 Meld 앞·중간·뒤에 직접 추가 가능
- 첫 등록 완료 후 `첫 등록 0/30` 문구 제거
- 로컬 변경이 없으면 `이번 제출 0점`도 표시하지 않음

## 5. Backend 경계

Backend Table Grid Validator의 행 범위를 Frontend 논리 Table과 같은 18행으로 통일했다.

```text
gridRow: 0..17 허용
gridRow: 18 이상 거부
gridColumn: 기존 18열 규칙 유지
Cell overlap: 거부
```

기존 Flyway V6의 `grid_row < 18` 제약과 일치하므로 신규 DB Migration은 필요하지 않다.

Legacy Persisted Coordinate Resolver도 18행 안에서 결정적으로 재배치하며, 이미 유효한 서버 좌표는 그대로 유지한다.

## 6. 변경하지 않은 영역

```text
인증 계약
Room/Game WebSocket Destination
STOMP Message Contract
Game Version / actionId Replay
DB Schema와 Flyway Migration
Docker Compose
Kubernetes Manifest
Classroom LAN Script
Native Pointer/Konva Spike
```

## 7. FINAL Gate

현재 산출물은 자동검증을 통과한 **FINAL Candidate**다.

아래를 사용자의 Windows/Docker Desktop 환경에서 통과한 뒤 Phase 7 FINAL로 확정한다.

```text
Backend 전체 Maven 테스트
2계정 첫 등록 전/후 Runtime
기존 Meld 재조합
Rack → 기존 Meld 추가
Undo / Cancel / Commit
혼잡 Table Scroll / Bottom Drop / Auto-scroll
새로고침 / 재접속
게임 포기 후 새 방 생성·입장
Console error 0 / warning 0
```
