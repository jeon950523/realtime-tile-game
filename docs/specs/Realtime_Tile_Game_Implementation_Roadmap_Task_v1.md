# 실시간 타일 보드게임 구현 단계별 작업지시서 v1

작성 기준: 2026-07-14  
문서 상태: 개발 착수용 확정안  
대상 스택:

```text
Backend
- Java 17
- Spring Boot
- Spring Security
- JWT
- Spring WebSocket/STOMP
- JPA
- MySQL

Frontend
- Vue 3
- Vite
- Pinia
- Axios
- STOMP Client
```

연결 문서:

- `Realtime_Tile_Game_Project_Planning_v1.md`
- `Realtime_Tile_Game_Rules_Spec_v1.md`
- `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `Realtime_Tile_Game_SRS_v1.md`
- `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- `Realtime_Tile_Game_REST_API_Spec_v1.md`
- `Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`
- `Realtime_Tile_Game_Server_GameState_Model_v1.md`
- `Realtime_Tile_Game_Rule_Engine_Class_Design_v1.md`
- `Realtime_Tile_Game_Test_Case_Matrix_v1.md`
- `Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md`

---

# 1. 작업 원칙

## 1-1. 개발 순서

```text
도메인 규칙
→ 인증
→ 방/로비
→ 실시간 게임
→ 시간 초과·재접속
→ CLASSIC 결과
→ 상대 전적
→ 랭킹
→ SPEED
→ 포트폴리오 정리
```

## 1-2. 기능 단위 완료 조건

각 기능은 다음이 모두 끝나야 완료다.

```text
코드 작성
→ 컴파일
→ 자동 테스트
→ 로컬 실행
→ 실제 브라우저 확인
→ 실패 케이스 확인
→ 포트폴리오 증거 로그 작성
```

## 1-3. AI 활용 원칙

생성형 AI는 다음을 지원한다.

- 코드 작성
- 테스트 코드 작성
- 버그 분석
- 리팩터링 후보
- 패치 작성
- 문서 초안

사용자는 다음을 직접 수행한다.

- 실행
- DB 구성
- 테스트
- 오류 재현
- 로그 확인
- 수정 결과 검증
- 최종 통과 판단
- 핵심 흐름 학습

---

# 2. 프로젝트 구조 권장

## Backend

```text
backend/
├─ src/main/java/.../
│  ├─ auth
│  ├─ user
│  ├─ room
│  ├─ game
│  │  ├─ domain
│  │  ├─ rule
│  │  ├─ application
│  │  ├─ runtime
│  │  ├─ infrastructure
│  │  └─ presentation
│  ├─ ranking
│  ├─ record
│  ├─ websocket
│  ├─ security
│  └─ common
│
└─ src/test/java/.../
```

## Frontend

```text
frontend/
├─ src/
│  ├─ api
│  ├─ components
│  ├─ composables
│  ├─ router
│  ├─ stores
│  ├─ views
│  ├─ types
│  ├─ utils
│  └─ assets
```

---

# 3. Phase 0 — 개발 환경과 뼈대

## 목표

백엔드·프론트·DB·WebSocket 기본 연결이 가능한 프로젝트 뼈대를 만든다.

## Backend 작업

- Spring Boot 프로젝트 생성
- Java 17 설정
- MySQL 연결
- JPA 설정
- Validation
- Spring Security
- WebSocket/STOMP
- 공통 응답 형식
- 공통 예외 처리
- Auditing

## Frontend 작업

- Vue 3 + Vite
- Pinia
- Vue Router
- Axios
- STOMP Client
- 공통 레이아웃
- 환경변수

## 완료 기준

- Backend 실행
- Frontend 실행
- DB 연결
- `/api/health` 성공
- WebSocket 연결 성공
- CORS 정상

## 산출물

- 실행 가이드
- 환경변수 예시
- 기본 README

---

# 4. Phase 1 — 타일 도메인과 규칙 엔진

## 목표

Spring 없이 RUN·GROUP·첫 등록·조커·재조합을 검증할 수 있게 한다.

## 구현 대상

```text
Tile
NumberTile
JokerTile
TileId
TileColor
TileSetFactory

RackState
TilePoolState
TableState
MeldState

RunValidator
GroupValidator
CompositeMeldValidator
InitialMeldValidator
RackContributionValidator
TileIntegrityValidator
TableRearrangementValidator
JokerRuleValidator
TurnCommitValidator
DeadlockEvaluator
SpeedScoreEvaluator
```

## 규칙

- JOKER 3개만으로 구성된 Meld는 실패 처리
- 동일 tileId 중복 금지
- 총 106개 불변
- 첫 등록 CLASSIC 30점
- SPEED 첫 등록 없음

## 테스트

`Realtime_Tile_Game_Test_Case_Matrix_v1.md`의 다음 범위:

```text
GAME-001~007
RUN-001~013
GROUP-001~010
INIT-001~011
TURN-001~009
JOKER-001~011
```

## 완료 기준

- 규칙 엔진 테스트 전체 통과
- Spring Context 없이 실행
- 실패 시 입력 상태 변경 없음
- 조커 역할 결과 확인 가능

## 포트폴리오 증거

- 규칙 엔진 클래스 구조
- RUN/GROUP 테스트 결과
- 조커 실패 케이스
- 106개 타일 무결성

---

# 5. Phase 2 — 인증과 사용자 프로필

## 목표

회원가입, 로그인, 토큰 재발급, 로그아웃, 프로필을 구현한다.

## Backend

- User Entity
- UserRepository
- AuthService
- JwtTokenProvider
- Refresh Token 정책
- PasswordEncoder
- SecurityFilterChain
- 내 프로필 API

## Frontend

- 회원가입 화면
- 로그인 화면
- 프로필 설정
- authStore
- Axios 인증 인터셉터
- 토큰 재발급 처리

## 테스트

```text
AUTH-001~010
```

## 완료 기준

- 이메일·닉네임 중복 검증
- 비밀번호 암호화
- 로그인 성공·실패
- 만료 토큰 재발급
- 로그아웃 후 재발급 차단
- 프로필 수정

## 포트폴리오 증거

- JWT 흐름도
- Refresh Token 처리
- 중복 가입 실패 화면
- 로그인 후 사용자 상태

---

# 6. Phase 3 — 로비와 대기방

## 목표

2~4인 방 생성·입장·준비·시작 흐름을 구현한다.

## Backend

- Room
- RoomPlayer
- 방 생성
- 방 목록
- 방 입장
- 방 나가기
- 방장 위임
- 준비 상태
- 게임 시작 조건
- 로비 WebSocket 이벤트
- 대기방 WebSocket 이벤트

## Frontend

- 로비
- 방 생성 모달
- 대기방
- 참가자 카드
- 준비 버튼
- 방장 시작 버튼

## 테스트

```text
ROOM-001~016
```

## 완료 기준

- 최대 인원 2·3·4 설정
- 최소 2인 시작
- 전원 준비
- 비방장 시작 차단
- 방장 이탈 위임
- 정원 초과 차단
- 방 목록 실시간 갱신

## 포트폴리오 증거

- 2인 대기방
- 4인 대기방
- 방장 위임
- 준비 상태 실시간 반영

---

# 7. Phase 4 — GameState와 게임 시작

## 목표

메모리 게임 세션과 초기 타일 분배를 구현한다.

## 구현 대상

```text
GameState
GamePlayerState
GameSession
GameSessionRegistry
TurnState
TurnSnapshot
PublicGameSnapshot
PrivatePlayerSnapshot
ProcessedActionRegistry
```

## 흐름

```text
게임 시작 요청
→ 참가자 고정
→ Game DB 생성
→ GamePlayer 생성
→ GameState 생성
→ 타일 106개 셔플
→ 각 14개 분배
→ 선 플레이어 결정
→ 공개·개인 상태 분리 전송
```

## 테스트

```text
GAME-004~011
WS-001~005
```

## 완료 기준

- 2인 풀 78개
- 3인 풀 64개
- 4인 풀 50개
- 상대 손패 원문 미전송
- 각 사용자 개인 손패 정상
- 현재 턴 정상

## 포트폴리오 증거

- 브라우저별 서로 다른 손패
- 공개 채널 메시지
- 개인 채널 메시지
- 타일 총량 로그

---

# 8. Phase 5 — 기본 턴과 드로우

## 목표

현재 턴, 드로우, PASS, 턴 순환을 구현한다.

## Backend

- DrawTileService
- PassTurnService
- 현재 턴 검증
- gameVersion
- actionId
- 다음 턴 생성
- TurnSnapshot 저장

## Frontend

- 현재 턴 표시
- 남은 시간 표시
- 드로우 버튼
- PASS 버튼
- 자신의 턴 외 조작 비활성화

## 테스트

```text
DRAW-001~005
PASS-001~004
CONC-001~004
```

## 완료 기준

- 정상 드로우
- 타일 원문 당사자만 수신
- 즉시 턴 전환
- 풀 비었을 때 PASS
- 중복 actionId 한 번만 실행
- 오래된 version 차단

---

# 9. Phase 6 — 손패 UI와 자동 정렬

## 목표

손패 표시·자유 정렬·777·789를 구현한다.

## Frontend

- gamePrivateStore
- turnDraftStore
- 손패 타일 컴포넌트
- 드래그 정렬
- 777 정렬
- 789 정렬
- 원래 순서
- 조커 오른쪽 배치

## 중요

자동 정렬은 서버 요청을 보내지 않는다.

## 테스트

```text
SORT-001~007
```

## 완료 기준

- 숫자 우선 정렬
- 색상·연속수 우선 정렬
- 조커 오른쪽
- 다른 플레이어 턴에도 정렬 가능
- 서버 게임 상태 미변경

---

# 10. Phase 7 — TurnDraft와 턴 확정

## 목표

클라이언트 임시 배치와 서버 최종 검증을 구현한다.

## Frontend

- 공개 테이블 드래그
- 새 Meld 생성
- Meld 분리·병합
- TurnDraft
- 턴 취소
- 턴 확정
- 오류 표시

## Backend

- TurnCommitRequest
- TurnCandidate
- TurnCommitService
- TurnCommitValidator 연결
- Candidate 검증 후 Commit
- 공개·개인 Snapshot 생성

## 테스트

```text
TURN-001~012
INIT-001~011
JOKER-001~011
CONC-005~008
```

## 완료 기준

- 첫 등록 30점
- 기존 테이블 재조합
- 조커 회수·재사용
- 손패 최소 1개 사용
- 검증 실패 시 GameState 불변
- 성공 시 version +1
- 중복 확정 1회 처리

## 포트폴리오 증거

- 첫 등록 성공
- 29점 실패
- 잘못된 재조합 실패
- 조커 회수
- 중복 확정 방지

---

# 11. Phase 8 — 서버 타이머와 시간 초과 롤백

## 목표

턴 제한시간과 원자적 롤백을 구현한다.

## Backend

- TurnTimeoutService
- 서버 스케줄러
- turnId 검증
- deadline 검증
- resolved 중복 방지
- TurnSnapshot 복원
- 자동 드로우 또는 PASS
- 다음 턴 전환

## Frontend

- 표시용 타이머
- 30초·10초 경고
- 시간 초과 메시지
- 최신 서버 상태 반영

## 테스트

```text
TIME-001~011
```

## 완료 기준

- 잘못된 배치 복원
- 유효 미확정 배치도 복원
- 조커 위치 복원
- 자동 드로우 1회
- 서버 타이머 기준
- 모든 브라우저 동일 상태

## 포트폴리오 핵심 영상

```text
잘못된 배치
→ 시간 초과
→ 배치 전 상태 복원
→ 자동 드로우
→ 다음 턴
```

---

# 12. Phase 9 — 재접속

## 목표

새로고침 또는 일시 연결 종료 후 기존 게임을 복원한다.

## Backend

- active-session API
- reconnect API
- PLAYER_DISCONNECTED
- PLAYER_RECONNECTED
- 중복 연결 처리
- stale Draft 폐기

## Frontend

- connectionStore
- 재연결 오버레이
- active-session 분기
- 공개·개인 상태 재적용
- WebSocket 재구독

## 테스트

```text
REC-001~008
```

## 완료 기준

- 자신의 손패 복원
- 상대 손패 미노출
- GamePlayer 중복 없음
- 현재 턴·남은 시간 유지
- 오래된 Draft 폐기

---

# 13. Phase 10 — CLASSIC 종료와 결과 저장

## 목표

손패 0개 승리, 교착 승리, DRAW를 구현한다.

## Backend

- WinConditionEvaluator
- DeadlockEvaluator
- GameFinishService
- GameResult
- GamePlayer 결과 저장
- 종료 중복 방지

## Frontend

- 결과 화면
- 승자
- 종료 사유
- 남은 손패
- 점수
- 로비 이동
- 재경기 대기방 복귀

## 테스트

```text
END-001~008
```

## 완료 기준

- 손패 0개 승리
- 무효 배치 승리 불가
- 교착 종료
- 최저 점수 동점 DRAW
- 종료 후 행동 차단
- 결과 1회 저장

---

# 14. Phase 11 — 상대 전적

## 목표

특정 상대와의 모드별 승·패·무를 조회할 수 있게 한다.

## Backend

- HeadToHeadRecord
- 게임 종료 시 참가자 쌍별 갱신
- 특정 상대 조회
- 목록 조회
- 최근 경기 조회

## Frontend

- 내 정보 화면
- 상대 전적 목록
- 특정 상대 상세

## 테스트

```text
H2H-001~010
```

## 완료 기준

- A 승리 = B 패배
- DRAW 양쪽 반영
- CLASSIC/SPEED 구분
- 4인 모든 참가자 쌍 갱신
- 중복 종료 재전송에도 1회 반영

---

# 15. Phase 12 — 랭킹

## 목표

CLASSIC 결과에 따라 ELO 계열 랭킹을 반영한다.

## Backend

- RatingCalculator
- RatingHistory
- 사용자 ratingScore
- 상대 평균 계산
- 결과 저장과 랭킹 트랜잭션
- 중복 적용 방지

## 공식

```text
expectedScore
= 1 / (1 + 10 ^ ((opponentAverageRating - playerRating) / 400))

ratingDelta
= round(32 × (actualScore - expectedScore))
```

```text
WIN = 1.0
DRAW = 0.5
LOSS = 0.0
```

## Frontend

- 내 랭킹
- 랭킹 목록
- 랭킹 이력
- 결과 화면 변동값

## 테스트

```text
RATE-001~012
```

## 완료 기준

- 낮은 상대 승리 보상 작음
- 높은 상대 승리 보상 큼
- DRAW 계산
- 다인전 자신 제외 평균
- SPEED 미반영
- 중복 랭킹 적용 차단

---

# 16. Phase 13 — SPEED 모드

## 목표

쉬는 시간·점심시간용 5분 점수 모드를 구현한다.

## 규칙

```text
전체 제한 5분
턴 제한 20초
첫 등록 없음
랭킹 미반영
```

## 점수

```text
최종 점수
= 손패에서 테이블에 확정한 타일 점수
- 종료 시 남은 손패 점수
```

## Backend

- SpeedGameModePolicy
- TileContribution
- SpeedScoreEvaluator
- gameDeadline
- 전체 시간 종료
- Draft 롤백
- 추가 드로우 없음

## Frontend

- SPEED 선택
- 전체 5분 타이머
- 현재 기여 점수
- 결과 점수
- 랭킹 미반영 표시

## 테스트

```text
SPEED-001~009
```

## 완료 기준

- 5분 종료
- 전체 시간 우선
- 현재 Draft 복원
- 추가 드로우 없음
- 점수 계산
- 최고 점수 동점 DRAW
- 랭킹 변화 없음

---

# 17. Phase 14 — 실제 2~4인 안정화

## 목표

학원 내부망에서 실제 사용자들이 플레이할 수 있게 한다.

## 확인 환경

```text
2인
3인
4인
Chrome 다중 프로필 또는 여러 PC
학원 내부망
```

## 테스트

- 2인 CLASSIC 전체 게임
- 4인 CLASSIC 동시접속
- 2~4인 SPEED
- 재접속
- 중복 클릭
- 네트워크 지연
- 브라우저 새로고침
- 상대 손패 개발자도구 확인
- 시간 초과

## 완료 기준

- 치명 오류 없음
- 타일 중복·유실 없음
- 게임 종료 가능
- 사용자별 상태 일치
- 상대 손패 노출 없음

---

# 18. Phase 15 — 포트폴리오 산출물

## 필수 자료

- 프로젝트 소개
- SRS
- ERD
- REST API
- WebSocket 명세
- GameState 구조
- 규칙 엔진 구조
- 테스트 결과
- 실제 플레이 영상
- 버그 해결 사례
- AI 활용 설명
- 회고

## 핵심 문제 해결 사례

1. 공개·개인 WebSocket 분리
2. actionId 중복 방지
3. gameVersion 충돌 방지
4. 시간 초과 Snapshot 롤백
5. 106개 타일 무결성
6. 재접속 복원
7. 랭킹 중복 반영 방지
8. 상대 전적 다인전 집계

---

# 19. 예상 일정

사용 가능 시간:

```text
평일 하루 약 5시간
주말 풀타임
```

## 핵심 CLASSIC MVP

```text
Phase 0~10
약 8~12일
```

## 랭킹·전적·SPEED 포함

```text
Phase 11~13
추가 3~5일
```

## 안정화·포트폴리오

```text
Phase 14~15
추가 2~3일
```

전체 안전 일정:

```text
약 2~3주
```

AI가 코드 작성을 지원하더라도 실행·버그 수정·실제 다중 사용자 검증에서 일정이 사용될 수 있다.

---

# 20. 기능별 작업 요청 형식

각 구현 요청은 다음 형식으로 진행한다.

```text
현재 단계:
Phase N

이번 작업:
기능명

참조 문서:
관련 md 파일

반드시 구현:
- 항목

금지:
- 다음 단계 기능 선행 구현
- 무관한 리팩터링
- 상대 손패 공개
- 상태 직접 수정 후 검증

테스트:
- 테스트 ID

산출물:
- 수정 파일 목록
- 패치 ZIP
- 완료 보고서 md
- 직접 확인 절차
```

---

# 21. 코드 구현 결과 검수 기준

AI가 코드 결과물을 제공하면 다음 순서로 검수한다.

```text
1. 변경 파일 확인
2. 요구 범위 밖 수정 확인
3. 컴파일
4. 자동 테스트
5. DB 변경 확인
6. 로컬 실행
7. 정상 시나리오
8. 실패 시나리오
9. 회귀 테스트
10. 포트폴리오 로그
```

전체 프로젝트 ZIP보다 수정·생성 파일 중심 패치 ZIP을 우선한다.

---

# 22. 금지 사항

- 상용 루미큐브 이미지·로고 복제
- 클라이언트 판정만으로 턴 확정
- 상대 손패 브로드캐스트
- gameVersion 없이 게임 변경
- actionId 없이 중복 가능 요청 처리
- 검증 전에 현재 GameState 직접 수정
- 시간 초과를 클라이언트 시간만으로 판정
- SPEED 결과 랭킹 반영
- 같은 게임 결과·랭킹·전적 중복 저장
- 테스트 없이 기능 완료 처리

---

# 23. 개발 착수 권장 순서

바로 시작할 첫 작업:

```text
Phase 0
프로젝트 뼈대 및 실행 환경 구성
```

그 다음:

```text
Phase 1
타일 도메인과 규칙 엔진
```

규칙 엔진이 안정된 뒤 인증과 WebSocket을 붙이는 것이 안전하다.

---

# 24. 다음 산출물

다음 단계:

1. 새 프로젝트 시작 프롬프트
2. 최종 문서 인덱스
3. 개발 착수 패키지 ZIP
