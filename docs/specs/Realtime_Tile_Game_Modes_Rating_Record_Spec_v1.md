# 실시간 타일 보드게임 게임 모드·랭킹·상대 전적 명세 v1

작성 기준: 2026-07-14  
상태: 설계 확정, 구현 우선순위는 CLASSIC 안정화 이후

---

## 1. 모드 구성

```text
CLASSIC
SPEED
```

### CLASSIC

- 첫 등록 30점
- 기본 턴 제한 120초
- 손패 0개 시 즉시 승리
- 교착 종료 시 남은 손패 점수 비교
- 랭킹 반영

### SPEED

- 전체 제한시간 5분
- 기본 턴 제한 20초
- 첫 등록 30점 없음
- 제한시간 종료 후 점수 비교
- 랭킹 미반영

---

## 2. 동점 처리

모든 최종 동점은 `DRAW`로 처리한다.

- CLASSIC 교착 종료 최저 점수 동점
- SPEED 최종 점수 동점
- 기타 승자를 한 명으로 결정할 수 없는 경우

---

## 3. SPEED 점수

```text
최종 점수
= 자신이 손패에서 테이블에 확정한 타일 점수
- 종료 시 남은 손패 점수
```

일반 타일은 표시 숫자만큼 계산한다.

조커:

- 테이블에 확정된 조커: 대체 숫자
- 손패에 남은 조커: 30점 감점

기존 테이블 타일을 이동하는 행동은 점수를 추가하지 않는다.

점수 변경은 턴 확정과 함께 반영하고, 턴 취소·검증 실패·시간 초과 롤백 시 함께 취소한다.

---

## 4. 랭킹 점수

### 초기값

```text
1000점
```

### 반영 대상

```text
CLASSIC: 반영
SPEED: 미반영
```

### 계산 기준

각 사용자는 자신을 제외한 참가자들의 경기 시작 전 평균 랭킹을 상대 점수로 사용한다.

```text
opponentAverageRating
= otherPlayersRatingSum / otherPlayerCount
```

### 기대 점수

```text
expectedScore
= 1 / (1 + 10 ^ ((opponentAverageRating - playerRating) / 400))
```

### 실제 점수

```text
승리 = 1.0
DRAW = 0.5
패배 = 0.0
```

### 변동값

```text
ratingDelta
= round(32 × (actualScore - expectedScore))
```

### 예시

내 점수 1000 기준:

```text
상대 평균 800에게 승리
→ 적은 점수 획득

상대 평균 1000에게 승리
→ 보통 점수 획득

상대 평균 1200에게 승리
→ 많은 점수 획득
```

패배는 반대로 작동한다.

```text
낮은 상대 평균에게 패배
→ 큰 점수 하락

높은 상대 평균에게 패배
→ 작은 점수 하락
```

DRAW도 기대 승률에 따라 소폭 상승 또는 하락할 수 있다.

---

## 5. 다인전 결과

### 단독 승리

승리자:

```text
actualScore = 1.0
```

나머지:

```text
actualScore = 0.0
```

### DRAW

동점으로 최종 승자를 정하지 못한 경우 해당 게임 결과를 DRAW로 기록한다.

모든 참가자:

```text
actualScore = 0.5
```

> 특정 참가자만 동점이고 나머지는 패배인 구조가 필요해질 경우 후속 규칙에서 별도 정의한다. 1차에서는 게임 전체 DRAW로 단순화한다.

---

## 6. 상대 전적

내 정보 화면에서 특정 상대와의 전적을 표시한다.

### 표시 항목

```text
상대 닉네임
상대 현재 랭킹
총 대전 수
CLASSIC 승/패/무
SPEED 승/패/무
전체 승률
최근 대전일
최근 경기 목록
```

### 전적 기록 관점

A가 B에게 승리:

```text
A 관점: WIN
B 관점: LOSS
```

DRAW:

```text
A 관점: DRAW
B 관점: DRAW
```

3~4인전에서는 한 게임 종료 시 모든 참가자 쌍에 대해 전적을 갱신한다.

4인전 예:

```text
참가자 A, B, C, D
→ A-B, A-C, A-D
→ B-C, B-D
→ C-D
```

---

## 7. 저장 구조

권장 필드:

```text
users.rating_score

games.game_mode
games.rating_applied

game_players.rating_before
game_players.rating_delta
game_players.rating_after
game_players.result_type

head_to_head_records
```

랭킹 적용 여부는 `games.rating_applied`로 막아 동일 게임이 중복 반영되지 않게 한다.

---

## 8. 구현 우선순위

```text
1. CLASSIC 핵심 규칙 엔진
2. 2~4인 실시간 플레이
3. 시간 초과·재접속 안정화
4. CLASSIC 결과 저장
5. 상대 전적
6. 랭킹 점수
7. SPEED 모드
8. SPEED 점수 및 5분 종료
```

DB와 클래스 구조에는 처음부터 `GameMode`, `ratingScore`, `resultType` 확장 지점을 둔다.

---

## 9. 테스트 핵심

- 낮은 상대 평균 승리 보상이 작음
- 높은 상대 평균 승리 보상이 큼
- 낮은 상대에게 패배 시 하락폭 큼
- 높은 상대에게 패배 시 하락폭 작음
- DRAW 계산 정확
- SPEED 랭킹 미반영
- 동일 게임 중복 반영 방지
- 3~4인 평균 점수 정확
- 참가자 쌍별 상대 전적 정확
