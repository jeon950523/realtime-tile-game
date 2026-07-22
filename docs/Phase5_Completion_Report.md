# Phase 5 Completion Report

작성 기준: 2026-07-15 KST

기준 전체본:

```text
phase0715-22-09-phase4-final-clean-source.zip
```

작업:

```text
Phase 5 — Minimum Current Turn, Draw And Pass Vertical Slice
```

## 구현 결과

```text
현재 턴 Draw
→ Pool 첫 Tile 1개 잠금
→ 본인 Rack 끝 이동
→ 공개 Event에는 Tile 상세 없음
→ JPA gameVersion 증가
→ 다음 실제 Seat
→ turnNumber 증가
→ 새 turnId·deadline
→ AFTER_COMMIT 공개·Private State
```

```text
Pool Empty PASS
→ consecutivePassCount 증가
→ 다음 턴
→ gameVersion 증가
→ AFTER_COMMIT 공개·Private State
```

## 핵심 구현

- Flyway V4 기존 Game Row Backfill
- Game Turn Runtime 영속 상태
- Game `PESSIMISTIC_WRITE`
- Pool 첫 Tile 행 잠금
- JPA `@Version` 외부 gameVersion
- Version 우선 검증
- Game 전용 actionId Replay
- Draw/PASS ACK
- AFTER_COMMIT `TILE_DRAWN`, `TURN_PASSED`, Private State
- Game STOMP SEND 인가
- Frontend Draw/PASS 버튼
- Server Deadline Countdown
- Version Race·REST 복구
- F5 Turn Runtime 복구

## Senior 검수

### 필수 수정 완료

- Draw 후 Rack 15개 Snapshot을 실패시키던 초기 Rack 정확히 14개 검증 수정
- 잘못된 turn 입력에서 Domain 부분 변경 가능성 제거
- Private Event·REST 복구 시 Frontend pending command lock 해제
- 기존 Game unique 테스트가 V4 NOT NULL에서 먼저 실패하지 않도록 Fixture 갱신

### 기능적 후속 개선

- Redis Replay
- Transactional Outbox
- Timeout·TurnDraft·Rollback Snapshot
- Game 종료·기권·장기 미접속

### 효율적 후속 개선

- Private State 조립의 Public State 재계산 공유
- 전원 Full Snapshot의 Delta 전환 검토
- Event/Action 문자열 상수·enum 통합

## 이번 작업 환경의 실제 검증 결과

### Backend

실행:

```bash
sh mvnw -q -DskipTests compile
```

실제 결과:

```text
Maven Wrapper가 repo.maven.apache.org를 DNS 해석하지 못해 배포본 다운로드 단계 중단
Compile 실행 0
Test 실행 0
```

### Frontend

실행:

```bash
npm ci --offline --ignore-scripts
```

실제 결과:

```text
오프라인 npm cache에 xmlchars-2.2.0.tgz가 없어 설치 단계 중단
Vitest 실행 0
TypeScript 전체 검사 실행 0
Production Build 실행 0
```

### 정적 검수

```text
git diff --check 통과
Changed/New Java 구문 오류 패턴 0
Changed/New TypeScript 구문 진단 0
Draw 공개 Payload 금지 Tile 상세 필드 0
```

정적 검수는 실제 Compile/Test/Build를 대체하지 않는다.

## 현재 판정

```text
코드 구현: 완료 후보
Senior 정적 검수: 완료
Backend 자동 검증: 환경 제한으로 미실행
Frontend 자동 검증: 환경 제한으로 미실행
MySQL V4: 미실행
Chrome 2계정 직접 검증: 미실행
Phase 5 최종 완료: 보류
```

## 최종 완료에 필요한 사용자 검증

1. Java 17 Backend 전체 테스트
2. Frontend `npm run check`
3. MySQL 8.4 V4와 기존 Row Backfill
4. 2계정 첫 Draw·두 번째 Draw
5. 공개 Event Tile 상세 0
6. F5 최신 Turn·Version·Deadline·Rack 복구
7. Pool 남음 PASS 차단
8. Health·Console 회귀

## 의도적으로 제외한 범위

- Table 제출·Meld·첫 등록 30점
- Rack Drag·자동 정렬
- TurnDraft·Timeout 자동 행동
- 게임 종료·기권
- Logout 자동 퇴장·Game 자동 만료
- SPEED
