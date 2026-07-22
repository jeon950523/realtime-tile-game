# Phase 6 완료 보고서

## 1. 구현 요약

Rummikub Online 참고 화면의 구성 원리를 가져와 전체 화면형 딥 블루 보드, 상대 좌석, 중앙 Meld 예정 영역, 목재형 2단 Rack, 좌측 정렬 컨트롤, 우측 Draw/PASS 컨트롤을 구현했다. 참고 화면의 상표·이미지를 복제하지 않고 CSS와 교체 가능한 Asset Registry로 구성했다.

서버의 `privateState.myRack`은 권위 데이터로 유지하고, 화면 순서는 별도 tileId 배열로 관리한다. 777, 789, 마지막 서버 수신 순서 복원, 자유 드래그, 상대 턴 정렬, 정렬·드래그 이동 모션, 정상 `TILE_DRAWN` 이후의 방향성 있는 Draw 진입 모션을 추가했다.

## 2. 필수 구현 결과

- 777: 숫자 오름차순, 같은 숫자에서 RED → BLUE → YELLOW → BLACK, stable tie-break, Joker 마지막
- 789: 색상 RED → BLUE → YELLOW → BLACK, 같은 색에서 숫자 오름차순, stable tie-break, Joker 마지막
- 원래 순서: 마지막 직접 REST/private snapshot의 서버 순서 복원
- 자유 드래그: Pointer Events와 pointer capture, 가장 가까운 타일 중심 기준 재배치, Rack 밖 drop 복원
- 상태 분리: 서버 Rack 객체 배열은 읽기 전용으로 사용하고 `displayOrderIds`만 변경
- 공개 이벤트 안전성: `GAME_STATE_UPDATED`만으로 표시 순서를 초기화하지 않음
- Draw 병합: private Rack 추가 tileId만 표시 순서에 병합하고 현재 정렬 모드를 재적용
- Draw 모션: 내 정상 `TILE_DRAWN`과 private Rack 추가가 어느 순서로 도착해도 한 번만 확정
- 상대 턴: 정렬·드래그 활성, Draw/PASS만 기존 턴 조건에 따라 비활성
- Motion: TransitionGroup move, drag 이동, Draw 영역 방향 진입, settle, reduced-motion 토큰 분리
- 외형 교체: theme CSS, motion CSS, Asset Registry 분리
- 2~4인 상대 배치: 인원수별 상대 좌석 위치 계산, 현재 턴 ring/countdown, 공개 Rack 개수만 표시
- Phase 7 경계: 중앙 Meld 영역은 안내만 제공하며 TurnDraft/타일 제출은 구현하지 않음
- 계약 보존: 기존 `drawTile()`/`passTurn()` 호출, 인증, 재접속, 상대 Rack 비공개 계약 유지

## 3. 선택 리팩터링

선택 리팩터링은 구현하지 않았다. 공통 보드게임 디자인 시스템, 모바일 전용 레이아웃, 전역 Toast 교체, Phase 7 Table Draft Engine 선구현 같은 범위 확장은 제외했다.

향후 선택 항목은 다음과 같다.

- `gameAssets.ts`의 빈 경로를 실제 라이선스 확보 이미지로 교체
- 브라우저 E2E를 CI에서 두 독립 context로 상시 실행
- Phase 7에서 Rack 표시 상태와 Table TurnDraft 사이의 명시적 이동 모델 추가

## 4. 자동 검증 결과

2026-07-17에 `frontend`에서 `npm run check`를 실제 실행했다.

- `vue-tsc --build`: 통과
- Vitest: 16개 파일, 120개 테스트 통과
- Vite production build: 통과, 140 modules transformed
- 결과: 종료 코드 0

## 5. 브라우저 Runtime 결과

기존 backend 코드를 수정하지 않고 Spring Boot test classpath와 임시 H2 메모리 DB로 실제 REST/WebSocket 런타임을 구동했다. 두 계정과 실제 게임을 만든 뒤 인앱 브라우저에서 순차 로그인해 검증했다.

- A: 최초 14장, 777/789/원래 순서 결과와 정렬 중 입력 잠금 확인
- Pointer drag: 첫 타일을 마지막으로 이동, 같은 tileId 집합 보존, MANUAL 전환 확인
- 무효 drag: Rack 밖 drop 후 직전 유효 순서 복원 확인
- 새로고침: 서버 snapshot 순서와 SERVER 모드 복원, Draw 진입 모션 미발생 확인
- 정상 Draw: 14 → 15장, Pool 78 → 77, Turn 1 → 2, 새 타일 `BLACK-05-B`에만 진입 모션 1회
- Draw CSS 실행값: `game-tile-draw-in, game-tile-settle`, `0.34s, 0.16s`
- 상대 턴 A: 777과 자유 drag 가능, public gameVersion 1 유지, Pool 77 유지
- B 로그인: 자신의 14장 상세만 표시, 상대 A는 Rack 15개만 표시
- B 789: Joker 마지막, public gameVersion 1 유지
- 1920×1080, 1366×768, 1280×720: document 가로·세로 overflow 없음, Rack/toolbar/action viewport 안 배치
- 최종 console error 0, warning 0, 화면 alert 0, `CONNECTED`
- 로그아웃과 두 번째 계정 재로그인 정상

## 6. 알려진 제한과 미실행 항목

- 실브라우저 검증은 영속 운영 DB가 아니라 임시 H2 메모리 DB를 사용했다.
- 두 계정은 한 브라우저에서 순차 로그인했다. 상대 Rack 공개 범위와 실제 서버 상태는 확인했지만 두 독립 창의 동시 시각 검증은 하지 않았다.
- Pool 0의 실제 PASS runtime과 서버가 거절한 Draw runtime은 게임을 소진하거나 서버 상태를 조작하지 않았다. 기존 자동 회귀 테스트와 이벤트 상관관계 단위 테스트로만 확인했다.
- 3인·4인 실제 WebSocket 게임은 만들지 않았다. 4인 상대 좌석 렌더링은 component test로 검증했다.
- 참고 이미지의 원본 그래픽 자산은 저작권·교체성 때문에 포함하지 않았으며 CSS 기본 외형을 사용한다.

## 7. 범위 준수

- Backend/API/WebSocket/DB 변경: 없음
- TurnDraft/타일 제출: Phase 7로 유지
- Draw/PASS 조건: 기존 store 명령과 조건 보존
- 상대 Rack: 개수만 공개, tileId/숫자/색상 비공개

