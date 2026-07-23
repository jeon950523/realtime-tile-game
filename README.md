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
- 가장 작은 빈 `seatOrder` 재사용
- 방 나가기, 마지막 참가자 이탈 시 방 종료, 방장 자동 위임
- 참가자 준비 상태 및 게임 시작 가능 조건 계산
- 트랜잭션 커밋 후 로비·대기방 이벤트 발행
- STOMP CONNECT JWT 인증
- 익명 Health 채널과 인증된 Lobby 채널 분리
- Room 채널 구독 시 멤버십 검증
- SEND·SUBSCRIBE 요청마다 사용자 상태와 JWT 만료 재검증
- `actionId` 기반 READY·START 중복 요청 방지
- 로비, 방 생성, 대기방 화면 구현
- 새로고침 후 기존 대기방 복구

## Phase 4 — 최소 게임 세션

- 방장 START 요청 시 Game·GamePlayer·GameTile 원자적 생성
- 표준 타일 106개 서버 셔플 및 참가자당 14개 분배
- 선 플레이어 결정 및 Room 상태를 `PLAYING`으로 전환
- 트랜잭션 커밋 후 GAME_STARTED·ROOM_REMOVED·개인 게임 상태 전송
- 공개 상태와 본인 Rack을 분리한 REST·WebSocket 응답
- 게임 화면 구현
- 새로고침 후 진행 중인 게임 복구
