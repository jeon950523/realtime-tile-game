# Phase 7 Unified Working Table 아키텍처

## 단일 작업판

내 턴이 시작되면 서버 `tableMelds`를 `baseline`으로 깊은 복사하고, 별도의 큰 TurnDraft 대신 같은 Table 위치에 `WorkingTableBoard`를 렌더링한다. 상대 턴에는 서버 Committed Table만 보이며 두 판은 동시에 표시하지 않는다.

`WorkingTableState`는 다음을 분리한다.

- `baseline`: 서버가 확정한 Table과 `gameVersion`
- `melds`: 현재 로컬 전체 Candidate Table
- `history`: Undo용 Working Table 스냅샷
- `pendingCommit`: 전송한 전체 Candidate와 예상 버전

## 편집 정책

- 첫 등록 전: baseline Meld 순서와 타일을 잠그고 Rack에서 만든 새 Meld만 뒤에 추가한다.
- 첫 등록 후: 기존 Meld 내 순서 변경, Meld 간 이동, 분리, 병합, Rack Tile 직접 추가를 허용한다.
- 유효하지 않은 중간 상태도 로컬에서는 유지한다. 확정 버튼만 비활성화한다.
- 현재 Phase에서는 Joker가 포함된 기존 Meld 편집을 UI에서 잠근다. 서버 Rule Engine이 최종 권위이며 Joker 회수·재사용 UX는 후속 범위다.

## Commit과 복구

클라이언트는 변경된 Meld 목록이 아니라 `meldId + tileIds`로 구성된 전체 Candidate Table을 보낸다. 성공은 Reply와 Private Snapshot의 도착 순서가 달라도 전송 Candidate와 서버 Table이 일치할 때만 확정한다.

- Cancel: baseline 복원
- 내 턴 종료/Timeout: 로컬 변경 폐기 후 최신 권위 상태 복원
- stale version: 최신 Snapshot으로 baseline과 Rack 재구성
- reconnect/private sync: 진행 중 로컬 후보 폐기 후 서버 권위 상태로 재구성
- 일반 validation reject: 사용자가 수정할 수 있도록 Working Table 유지

Rack slot 이동과 drag overlay 이동의 책임은 계속 분리된다. Phase 6의 복수 타일 Overlay 자료구조와 Hold Group Drag는 Rack→Working Table 입력에 재사용한다.

