# Phase 4 구현 완료 보고서

작성 기준: 2026-07-15 KST

최종 기준 전체본:

```text
phase0715-22-09-phase4-final-clean-source.zip
```

작업:

```text
Phase 4 — Minimum Game Session And Initial Deal Vertical Slice
```

## 최종 판정

```text
Phase 4 최종 완료
```

이 문서는 최초 생성 당시 Backend·MySQL·브라우저 검증이 보류였던 이력을 숨기지 않는다. 이후 사용자 환경의 Java 17, MySQL 8.4, Chrome 일반 창·시크릿 창에서 검증을 완료했고, 2026-07-15 최종 완료로 갱신했다.

## 구현 결과

```text
방장 START
→ Game/GamePlayer/GameTile 원자적 생성
→ 표준 106개 서버 Random 순서
→ 참가자당 14개 분배
→ 선 플레이어 결정
→ Room PLAYING
→ AFTER_COMMIT GAME_STARTED / ROOM_REMOVED / Game State
→ 전 참가자 GameView 이동
→ 공개 상태와 본인 Rack 분리
→ Active Game REST 새로고침 복구
```

## 최종 자동 검증

### Backend

```text
Tests run: 258
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
Java 17
```

### Frontend

```text
Vitest 77개 통과
TypeScript 통과
Production Build 통과
```

## 최종 실행 검증

```text
MySQL V1·V2·V3 정상
회원가입·로그인 정상
2계정 방 생성·입장·READY 정상
방장 시작 정상
두 계정 같은 gameId 진입
Game 1개
GamePlayer 2개
GameTile 106개
각 Rack 14개
2인 Pool 78개
현재 턴 참가자 1명
상대 Rack 상세 비공개
본인 Rack만 상세 공개
Game WebSocket 정상
```

같은 색상·숫자 타일이 양쪽 Rack에 하나씩 존재할 수 있다. 표준 106개 세트가 같은 색·숫자 조합을 A/B 두 장씩 가지므로 정상이다.

## 핵심 구현

- Flyway V3 `games`, `game_players`, `game_tiles`
- `GameStartService`, `GameQueryService`
- 주입 가능한 `GameStartRandomizer`
- 106개·초기 Rack 14·Pool Count·Tile Unique 불변식
- Room WAITING → PLAYING 의도 메서드
- PLAYING Room 대기방 Leave 차단
- `GET /api/games/{gameId}`
- `GET /api/me/active-game`
- Public/Private DTO 분리
- Game Topic GamePlayer Membership 인가
- Broker Topic 직접 SEND 차단
- AFTER_COMMIT Game Event
- `/games/:gameId`와 `GameView`
- Active Game 우선 Router 복구
- Game Topic·Private Queue 중복 구독 방지

## 정책 경계

로그아웃·브라우저 종료는 Game Session 탈퇴가 아니다.

```text
로그아웃
→ 인증·WebSocket 종료
→ Room PLAYING·Game IN_PROGRESS 유지
→ 재로그인 Active Game 복구
```

자동 만료·기권·온라인 상태·전원 장기 미접속 ABANDONED 처리는 후속 단계로 이월한다.

## Phase 4에서 의도적으로 제외한 범위

- Draw Command
- Play Tile Command
- Meld Commit
- Table Rearrangement
- Joker Replacement
- Turn End·Timer
- Game End·Winner·Score
- SPEED Mode
- Spectator·Chat
- Redis·Multi Server
