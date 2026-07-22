# Phase 7 TurnDraft and Commit Flow

## 권위 분리

```text
서버 권위 Rack ─┬─ 표시 Rack
                └─ 로컬 TurnDraft

표시 Rack tileIds ∪ TurnDraft tileIds = 서버 권위 Rack tileIds
표시 Rack tileIds ∩ TurnDraft tileIds = ∅
```

TurnDraft는 Pinia의 공개/개인 GameState에 섞지 않는다. 새로고침·재접속·stale version에서는 서버 상태가 항상 우선한다.

## Hold Group Drag

- 단일 타일 Pointer Drag는 즉시 시작한다.
- 789/777 정렬 모드에서 시각 그룹이 있으면 320ms Hold 뒤 `activeDragTileIds`를 복수 타일로 승격한다.
- 6px를 넘는 조기 이동은 Hold 승격을 취소한다.
- Joker는 자동 시각 그룹에서 제외한다.
- 복수 타일 Overlay는 수평으로 렌더링한다.
- Rack slot/placeholder 이동과 Overlay 포인터 이동은 서로 다른 DOM 계층과 책임을 유지한다.
- Phase 6의 fixed slot, RAF throttle, dead zone, teleport overlay, blur/lostpointercapture 정리를 유지한다.

## Draft 편집

지원 동작:

- 새 Meld 생성
- 기존 Draft Meld에 타일 추가
- Meld 내부 순서 변경
- Draft Meld 사이 단일 타일 이동
- 타일 또는 Meld 전체 Rack 반환
- Undo
- Cancel과 원래 표시 순서 복원

각 Draft Meld는 프론트에서 RUN/GROUP/INVALID와 점수를 즉시 표시하지만, 이 값은 사용자 피드백용이다. 서버 COMMIT은 클라이언트 점수나 타입을 받지 않는다.

## COMMIT 메시지

```json
{
  "actionId": "uuid",
  "gameVersion": 0,
  "melds": [
    { "clientMeldId": "uuid", "tileIds": ["RED-07-A", "RED-08-A", "RED-09-A"] },
    { "clientMeldId": "uuid", "tileIds": ["BLUE-01-A", "BLUE-02-A", "BLUE-03-A"] }
  ]
}
```

Destination은 `/app/games/{gameId}/turn/commit`이다. 게임 참가자만 전송할 수 있고, `actionId` replay와 `gameVersion` 검증을 적용한다.

## 성공 동기화

1. 클라이언트는 Draft를 pending으로 잠근다.
2. 서버가 DB COMMIT 후 `MELDS_COMMITTED`와 사용자별 GameState를 전송한다.
3. 공개 이벤트가 개인 Rack보다 먼저 와도 Draft를 조기 삭제하지 않는다.
4. 개인 상태 sync revision에서 제출 타일이 Rack에서 사라지고 Meld ID가 Table에 존재하는지 확인한다.
5. 확인 후에만 Draft를 `COMMITTED`로 정리한다.

검증 실패는 Draft를 유지하고 pending만 푼다. stale version은 Draft를 폐기하고 최신 권위 상태를 다시 읽는다.

