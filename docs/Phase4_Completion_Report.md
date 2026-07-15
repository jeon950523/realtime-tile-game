# Phase 4 구현 완료 보고서

작성 기준: 2026-07-15 KST

기준 전체본:

```text
phase0715-16-40-phase3-final-clean-source.zip
```

작업:

```text
Phase 4 — Minimum Game Session And Initial Deal Vertical Slice
```

## 구현 결과

다음 수직 흐름을 코드로 연결했다.

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

## 변경 규모

- 신규: 50개
- 수정: 23개
- 삭제: 1개
- 수정·생성 합계: 73개

## 핵심 구현

- Flyway V3 `games`, `game_players`, `game_tiles`
- `GameStartService`, `GameQueryService`
- 주입 가능한 `GameStartRandomizer`
- 106개·Rack 14·Pool Count·Tile Unique 불변식
- Room WAITING → PLAYING 의도 메서드
- PLAYING Room 대기방 Leave 차단
- `GET /api/games/{gameId}`
- `GET /api/me/active-game`
- Public/Private DTO 분리
- Game Topic GamePlayer Membership 인가
- Broker Topic 직접 SEND 차단 유지
- AFTER_COMMIT Game Event
- `/games/:gameId`와 `GameView`
- Active Game 우선 Router 복구
- Game Topic·Private Queue 중복 구독 방지

## 자동 테스트 결과

### Backend

실행 명령:

```bash
./mvnw clean test
```

실제 결과:

```text
검수 환경 Java: 21.0.10
Maven Wrapper 다운로드 단계에서 repo.maven.apache.org DNS 해석 실패
Exit Code 6
실행 테스트 0
Failures/Errors/Skipped/BUILD SUCCESS 확인 불가
```

기존 기준 221개와 신규 소스 기준 37회 호출을 합친 **예상 258개**는 테스트 계획 수치일 뿐 통과 수가 아니다.

외부 의존성이 없는 `javac --release 17 -proc:none` 정적 스캔에서는 missing dependency 오류만 발생했고 Java 구문 오류 패턴은 0건이었지만, Maven Compile이나 Spring Context 검증을 대체하지 않는다.

### Frontend

실제 결과:

```text
npm ci: 성공
Vitest: 13 files / 77 tests passed
TypeScript: 통과
Production Build: 통과
Vite: 119 modules transformed
```

검수 환경에서 `npm run check`의 중첩 npm 프로세스가 간헐적으로 종료되지 않아, 동일 구성 요소를 `npm run type-check`, Vitest 단일 Worker 실행, `npm run build-only`로 각각 확정했다. 사용자 환경에서는 표준 `npm run check`를 다시 실행해야 한다.

## 현재 판정

```text
코드 구현: 완료 후보
Frontend 자동 검증: 통과
Backend 자동 검증: 미실행
MySQL 8.4 검증: 미실행
Chrome 2계정 검증: 미실행
Phase 4 최종 완료: 보류
```

## 미구현 경계

다음은 의도적으로 구현하지 않았다.

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

## 알려진 한계

- 단일 서버 메모리 `ActionReplayStore` 사용
- 게임 종료가 없으므로 생성된 Game은 계속 IN_PROGRESS
- Private State 조회 시 106개 Tile 전체를 읽음
- WebSocket 전송 실패 재처리용 Outbox 없음; REST Snapshot 복구를 원본으로 사용

## 최종 완료 전 필수

1. 사용자 Java 17에서 Backend 전체 테스트 통과
2. MySQL 8.4 V3 Migration과 제약 확인
3. Chrome 일반·시크릿 A/B 동일 gameId 이동
4. Rack 14·Pool 78·상대 Tile 상세 0
5. F5 동일 상태 복구
6. Health 회귀·Console Error 0
