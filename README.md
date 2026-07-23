# Realtime Tile Game

2~4인이 함께 즐기는 실시간 숫자 타일 보드게임 프로젝트입니다.

## Phase 0 — 프로젝트 기반 구축

- Spring Boot 기반 REST API 및 MySQL 연동
- CORS 설정과 공통 오류 응답 규격 구성
- 공개 WebSocket/STOMP Health 채널 구현
- Vue 3, Vite, Pinia, Vue Router, Axios, STOMP 기반 프론트엔드 구성

## Phase 1 — 순수 규칙 엔진

- 표준 타일 106개 구성 및 2~4인 초기 타일 분배
- RUN, GROUP, 첫 등록, 테이블 재조합, 조커 규칙 검증
- 타일 무결성 검증
- CLASSIC·SPEED 모드 정책 구현
- 게임 종료 조건 및 점수 계산
- Spring, JPA, WebSocket과 분리된 순수 Java 도메인 설계

## Phase 2 — 인증 및 프로필

- 회원가입·로그인 및 BCrypt 비밀번호 암호화
- JWT Access Token과 HttpOnly Refresh Cookie 적용
- Refresh Token 해시 저장·회전·재사용 차단
- 보호된 REST API에서 ACTIVE 사용자 상태 공통 검증
- 프로필 조회 및 닉네임·아바타 수정
- 프론트엔드 Access Token 메모리 관리
- 401 응답 Single Flight 처리 및 새로고침 후 인증 복구

## Phase 3 — 로비 및 대기방

- 2·3·4인 공개 CLASSIC 방 생성
- 방 목록·빠른 입장 후보·방 상세·현재 대기방 REST API
- 사용자당 하나의 활성 방 제한
- 정원 및 좌석 경쟁 처리를 위한 비관적 잠금
- 빈 `seatOrder` 재사용, 방장 자동 위임, 마지막 참가자 이탈 시 방 종료
- 참가자 준비 상태 및 게임 시작 가능 조건 계산
- 트랜잭션 커밋 후 로비·대기방 이벤트 발행
- STOMP CONNECT JWT 인증 및 채널별 접근 권한 검증
- `actionId` 기반 READY·START 중복 요청 방지
- 로비, 방 생성, 대기방 화면 및 새로고침 복구 구현

## Phase 4 — 최소 게임 세션

- 방장 START 요청 시 Game·GamePlayer·GameTile 원자적 생성
- 표준 타일 106개 서버 셔플 및 참가자당 14개 분배
- 선 플레이어 결정 및 Room 상태를 `PLAYING`으로 전환
- 트랜잭션 커밋 후 GAME_STARTED·ROOM_REMOVED·개인 게임 상태 전송
- 공개 상태와 본인 Rack을 분리한 REST·WebSocket 응답
- 게임 화면 및 새로고침 후 진행 중인 게임 복구

## Phase 5 — 기본 턴과 드로우

- 현재 턴 검증과 DRAW·Pool Empty PASS 구현
- 드로우한 타일 상세 정보의 당사자 전용 전달
- 명령 처리 후 다음 참가자로 턴 전환
- `gameVersion` 기반 오래된 요청 차단
- `actionId` 기반 중복 명령 Replay 처리
- 공개 게임 상태와 사용자별 Private Rack 동기화
- 진행 중인 게임 나가기 및 종료 상태 처리

### Phase 5.5-A — Docker Compose 배포

- MySQL·Backend·Frontend Nginx 통합 실행 구성
- Java 17·Node 22 Multi-stage Build와 Non-root Runtime 적용
- SPA Fallback 및 REST·WebSocket Reverse Proxy 구성
- Same-origin Endpoint와 서비스 Health Check 적용

### Phase 5.5-B — 로컬 Kubernetes 배포

- Docker Desktop Kubernetes용 Namespace와 Manifest 구성
- MySQL StatefulSet·PVC 및 Backend·Frontend Deployment 구성
- ConfigMap·Secret 분리와 MySQL 대기 Init Container 적용
- Startup·Readiness·Liveness Probe 및 Resource 제한 적용
- Frontend NodePort 외부 노출과 Backend·MySQL ClusterIP 구성

### Phase 5.5-C — 학원 내부망 배포

- Windows Host의 사설 LAN IPv4 선택 및 제한적 Port Forward 구성
- localhost·LAN Origin을 반영한 Backend CORS 갱신
- Windows Firewall `LocalSubnet` 전용 규칙 관리
- 내부망 환경의 Start·Stop·Status·Test 도구 구성
- PID 재사용·중복 실행 방어 및 Workload·PVC 유지 처리

## Phase 6 — 손패 UI와 자동 정렬

- Rummikub 형태의 게임 보드와 손패 UI 구현
- 서버 Rack 상태와 클라이언트 표시 순서 분리
- 789·777·원래 순서 정렬 및 조커 우측 배치
- 다른 참가자의 턴에도 가능한 로컬 손패 정렬
- 고정 슬롯, Drag Ghost·Placeholder 및 Dead Zone 적용
- 드로우 진입과 손패 이동 애니메이션 구현

## Phase 7 — TurnDraft와 턴 확정

- 로컬 Working Table 기반 TurnDraft 편집·Undo·Cancel 구현
- Hold Group Drag와 복수 타일 Overlay 지원
- 새 Meld 생성 및 기존 Meld 수정·분리·병합
- 같은 턴에 등록한 여러 Meld의 첫 등록 30점 합산 검증
- 조커 회수·재사용 및 손패 타일 사용 규칙 검증
- 전체 Candidate Table을 서버에서 최종 검증한 뒤 원자적으로 확정
- `game_melds`와 TABLE 타일 위치·그리드 좌표 영속화
- COMMIT 중복 실행 방지와 `gameVersion` 충돌 처리
- Rack·첫 등록 상태·버전·다음 턴의 원자적 갱신
- 공개 Table과 사용자별 Private Rack 실시간 동기화
- Working Table 자동 재배치, 충돌 회피, 행 Wrap 및 Edge Auto-scroll
- 직접 배치한 타일의 Meld 병합 오류 수정
