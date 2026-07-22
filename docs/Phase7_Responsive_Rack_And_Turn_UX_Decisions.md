# Phase 7 Responsive Rack과 Turn UX 결정

## Adaptive Rack

`computeAdaptiveRackLayout(tileCount, availableWidth)`가 local CSS variable을 계산한다.

- 20장 이하: 최대 10열
- 21장 이상: 두 행 고정, 열 수는 `ceil(tileCount / 2)`
- 21장: 11열 × 2행
- 30장: 15열 × 2행
- tile width: 가용 폭 기준 42~82px
- tile height: 기존 82:96 비율 유지
- gap: 좁은 폭 3px, 그 외 5px
- rack height: 행 수와 실제 tile height에서 계산

`ResizeObserver`가 Rack 폭 변경만 반영한다. 서버 Rack 순서, 정렬 결과, Phase 6 고정 slot geometry·RAF·Dead Zone·Teleport Overlay는 유지한다.

내 턴에는 Rack과 Action 영역에 강한 녹색 border/glow가 표시되고 `내 턴` badge가 붙는다. 상대 턴에는 강조가 제거된다. `prefers-reduced-motion`에서는 pulse를 제거한다.

상태 문구는 첫 등록 전 `첫 등록 X/30`, 완료 후 `첫 등록 완료`와 `이번 제출 N점`을 사용한다. 전체 Candidate가 유효하고 Rack 기여가 있을 때만 Commit을 활성화한다.

