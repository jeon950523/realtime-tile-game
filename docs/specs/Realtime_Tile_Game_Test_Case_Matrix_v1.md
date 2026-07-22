# 실시간 타일 보드게임 테스트 케이스 상세표 v1

작성 기준: 2026-07-14  
문서 상태: 1차 MVP + CLASSIC/SPEED + 랭킹·상대 전적 검증 기준  
목적: 각 요구사항의 성공·실패·동시성·복구·보안 조건을 테스트 ID로 관리한다.

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

---

# 1. 테스트 단계

## Unit

순수 규칙 엔진과 계산 로직.

## Integration

Spring Context, JPA, 트랜잭션, WebSocket Handler, GameSession.

## API

REST 요청·응답·권한·Validation.

## E2E

브라우저 2~4개를 사용한 실제 플레이.

## Manual Portfolio Evidence

영상, 스크린샷, 로그와 재현 절차를 남기는 테스트.

---

# 2. 우선순위

```text
P0
게임 무결성, 보안, 상태 중복, 턴 원자성

P1
핵심 게임 규칙과 재접속

P2
사용성, 랭킹, 전적, 스피드 모드

P3
표시·정렬·세부 UX
```

---

# 3. 공통 테스트 데이터

## 사용자

```text
U1: rating 1000
U2: rating 800
U3: rating 1000
U4: rating 1200
```

## 타일 ID 예시

```text
R3A = RED-3-A
R4A = RED-4-A
R5A = RED-5-A
B7A = BLUE-7-A
Y7A = YELLOW-7-A
K7A = BLACK-7-A
JA  = JOKER-A
JB  = JOKER-B
```

## 모드

```text
CLASSIC
SPEED
```

---

# 4. 인증 및 프로필

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| AUTH-001 | P1 | API | 정상 회원가입 | 201, 사용자 생성, 비밀번호 암호화 |
| AUTH-002 | P1 | API | 이메일 중복 가입 | 409 EMAIL_ALREADY_EXISTS |
| AUTH-003 | P1 | API | 닉네임 중복 | 409 NICKNAME_ALREADY_EXISTS |
| AUTH-004 | P1 | API | 비밀번호 확인 불일치 | 400 PASSWORD_CONFIRM_MISMATCH |
| AUTH-005 | P1 | API | 정상 로그인 | Access Token 발급, Refresh Cookie 생성 |
| AUTH-006 | P1 | API | 잘못된 비밀번호 | 401 INVALID_CREDENTIALS |
| AUTH-007 | P1 | API | 만료 Refresh Token | 재발급 거부 |
| AUTH-008 | P1 | API | 로그아웃 후 재발급 | 차단 |
| AUTH-009 | P2 | API | 닉네임 수정 | 정상 반영 |
| AUTH-010 | P2 | API | 삭제 사용자 로그인 | 거부 |

---

# 5. 방 및 로비

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| ROOM-001 | P1 | API | 최대 2인 방 생성 | 성공 |
| ROOM-002 | P1 | API | 최대 3인 방 생성 | 성공 |
| ROOM-003 | P1 | API | 최대 4인 방 생성 | 성공 |
| ROOM-004 | P1 | API | 최대 인원 1 | INVALID_MAX_PLAYERS |
| ROOM-005 | P1 | API | 최대 인원 5 | INVALID_MAX_PLAYERS |
| ROOM-006 | P1 | API | CLASSIC 기본 제한시간 | 120초 |
| ROOM-007 | P2 | API | SPEED 기본 제한시간 | 턴 20초, 전체 300초 |
| ROOM-008 | P1 | Integration | 정원 가득 찬 방 입장 | ROOM_FULL |
| ROOM-009 | P1 | Integration | 게임 중 방 입장 | ROOM_ALREADY_PLAYING |
| ROOM-010 | P1 | Integration | 한 사용자의 중복 방 참가 | 차단 |
| ROOM-011 | P1 | Integration | 방장 이탈 | 다음 입장 순서 사용자에게 위임 |
| ROOM-012 | P1 | Integration | 참가자 1명에서 시작 | 차단 |
| ROOM-013 | P1 | Integration | 참가자 2명 준비 완료 | 시작 성공 |
| ROOM-014 | P1 | Integration | 한 명 준비 미완료 | 시작 차단 |
| ROOM-015 | P1 | WebSocket | 준비 상태 변경 | 방 참가자에게 브로드캐스트 |
| ROOM-016 | P2 | WebSocket | 로비 방 인원 변경 | 목록 갱신 |

---

# 6. 게임 생성 및 타일 분배

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| GAME-001 | P0 | Unit | 타일 세트 생성 | 고유 tileId 106개 |
| GAME-002 | P0 | Unit | 동일 색상·숫자 2개 | 서로 다른 ID |
| GAME-003 | P0 | Unit | 조커 생성 | 2개 |
| GAME-004 | P0 | Integration | 2인 초기 분배 | 각 14개, 풀 78개 |
| GAME-005 | P0 | Integration | 3인 초기 분배 | 각 14개, 풀 64개 |
| GAME-006 | P0 | Integration | 4인 초기 분배 | 각 14개, 풀 50개 |
| GAME-007 | P0 | Unit | 전체 위치 합산 | 정확히 106개 |
| GAME-008 | P1 | Integration | 선 플레이어 결정 | 참가자 중 1명 |
| GAME-009 | P1 | Integration | seatOrder 순환 | 2·3·4인 모두 정상 |
| GAME-010 | P0 | WebSocket | 게임 시작 공개 이벤트 | 손패 원문 없음 |
| GAME-011 | P0 | WebSocket | 개인 초기 손패 이벤트 | 당사자 손패만 포함 |

---

# 7. RUN 검증

| ID | 우선순위 | 유형 | 입력 | 기대 결과 |
|---|---|---|---|---|
| RUN-001 | P1 | Unit | R3,R4,R5 | 성공 |
| RUN-002 | P1 | Unit | R1~R13 | 성공 |
| RUN-003 | P1 | Unit | R3,R4 | 실패 |
| RUN-004 | P1 | Unit | R3,B4,R5 | 실패 |
| RUN-005 | P1 | Unit | R3,R5,R6 | 실패 |
| RUN-006 | P1 | Unit | R3,R3,R4 | 실패 |
| RUN-007 | P1 | Unit | R12,R13,R1 | 실패 |
| RUN-008 | P1 | Unit | R3,J,R5 | J=R4 성공 |
| RUN-009 | P1 | Unit | J,R4,R5 | 가능한 R3 배정 |
| RUN-010 | P1 | Unit | R11,R12,J | J=R13 |
| RUN-011 | P1 | Unit | R12,J,J | R13 이후 불가 조합 여부 검증 |
| RUN-012 | P1 | Unit | J,J,J | 정책상 유효 역할 결정 또는 명시적 실패 |
| RUN-013 | P0 | Unit | 동일 tileId 2회 | DUPLICATED_TILE |

> `J,J,J`의 허용 여부는 구현 전에 최종 정책을 하나로 고정해야 한다. 권장안은 문맥이 완전히 불명확하므로 실패 처리다.

---

# 8. GROUP 검증

| ID | 우선순위 | 유형 | 입력 | 기대 결과 |
|---|---|---|---|---|
| GROUP-001 | P1 | Unit | R7,B7,Y7 | 성공 |
| GROUP-002 | P1 | Unit | R7,B7,Y7,K7 | 성공 |
| GROUP-003 | P1 | Unit | R7,B7 | 실패 |
| GROUP-004 | P1 | Unit | R7,R7,B7 | 실패 |
| GROUP-005 | P1 | Unit | R7,B8,K7 | 실패 |
| GROUP-006 | P1 | Unit | 5개 타일 | 실패 |
| GROUP-007 | P1 | Unit | R7,B7,J | 성공 |
| GROUP-008 | P1 | Unit | R7,J,J | 남은 색상 배정 성공 |
| GROUP-009 | P1 | Unit | J,J,J | 정책상 실패 권장 |
| GROUP-010 | P0 | Unit | 동일 tileId 중복 | DUPLICATED_TILE |

---

# 9. 첫 등록

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| INIT-001 | P1 | Unit | 합계 정확히 30 | 성공 |
| INIT-002 | P1 | Unit | 합계 29 | 실패 |
| INIT-003 | P1 | Unit | 합계 31 | 성공 |
| INIT-004 | P1 | Unit | 여러 조합 합계 30 | 성공 |
| INIT-005 | P1 | Unit | 기존 테이블 타일 사용 | 실패 |
| INIT-006 | P1 | Unit | 기존 테이블 재배치 | 실패 |
| INIT-007 | P1 | Unit | 조커 포함 30 이상 | 성공 |
| INIT-008 | P1 | Unit | 조커 대체 숫자 점수 | 정확히 반영 |
| INIT-009 | P1 | Integration | 첫 등록 성공 | initialMeldCompleted=true |
| INIT-010 | P0 | Integration | 첫 등록 실패 | 상태·버전 불변 |
| INIT-011 | P2 | Unit | SPEED 첫 등록 | 검사 생략 |

---

# 10. 일반 턴 및 재조합

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| TURN-001 | P1 | Unit | 손패 타일 1개 사용 | 확정 가능 |
| TURN-002 | P1 | Unit | 기존 테이블만 재배치 | NO_RACK_TILE_USED |
| TURN-003 | P1 | Unit | 긴 RUN 분리 | 최종 조합 모두 유효하면 성공 |
| TURN-004 | P1 | Unit | GROUP 4번째 색 추가 | 성공 |
| TURN-005 | P1 | Unit | 기존 타일을 손패로 이동 | 실패 |
| TURN-006 | P1 | Unit | 하나의 Meld만 무효 | 전체 실패 |
| TURN-007 | P0 | Unit | 기존 타일 누락 | MISSING_TILE |
| TURN-008 | P0 | Unit | 타일 복제 | DUPLICATED_TILE |
| TURN-009 | P0 | Integration | 검증 실패 | GameState 완전 불변 |
| TURN-010 | P1 | Integration | 성공 확정 | version +1 |
| TURN-011 | P1 | Integration | 다음 턴 전환 | seatOrder 기준 |
| TURN-012 | P0 | Integration | 현재 턴 아닌 사용자 | NOT_CURRENT_TURN |

---

# 11. 조커

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| JOKER-001 | P1 | Unit | RUN 중앙 대체 | 성공 |
| JOKER-002 | P1 | Unit | GROUP 색상 대체 | 성공 |
| JOKER-003 | P1 | Unit | 실제 타일로 교체 | 성공 |
| JOKER-004 | P1 | Unit | 잘못된 숫자로 교체 | 실패 |
| JOKER-005 | P1 | Unit | 첫 등록 전 회수 | 실패 |
| JOKER-006 | P1 | Unit | 회수 후 같은 턴 재사용 | 성공 |
| JOKER-007 | P1 | Unit | 회수 후 손패 보관 | RETRIEVED_JOKER_NOT_REUSED |
| JOKER-008 | P1 | Unit | 조커 포함 조합 재분리 | 최종 전체 유효 시 성공 |
| JOKER-009 | P0 | Integration | 실패 후 조커 위치 | 원상복구 |
| JOKER-010 | P2 | Unit | SPEED 기여 조커 점수 | 대체 숫자 반영 |
| JOKER-011 | P2 | Unit | SPEED 잔여 조커 | 30점 감점 |

---

# 12. 드로우 및 PASS

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| DRAW-001 | P1 | Integration | 정상 드로우 | 손패 +1, 풀 -1 |
| DRAW-002 | P1 | Integration | 드로우 후 턴 전환 | 즉시 다음 턴 |
| DRAW-003 | P1 | E2E | 드로우 타일 | 당사자만 원문 확인 |
| DRAW-004 | P1 | Integration | 임시 배치 후 드로우 | 배치 전 복원 후 드로우 |
| DRAW-005 | P1 | Integration | 풀 비었는데 드로우 | DRAW_POOL_EMPTY |
| PASS-001 | P1 | Integration | 풀 비었을 때 PASS | 성공 |
| PASS-002 | P1 | Integration | 풀이 남았는데 PASS | 실패 |
| PASS-003 | P1 | Integration | 연속 PASS 수 | 정확히 증가 |
| PASS-004 | P1 | Integration | 유효 턴 이후 | 연속 PASS 0으로 초기화 |

---

# 13. 시간 초과

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| TIME-001 | P0 | Unit | deadline 전 실행 | 처리 거부 |
| TIME-002 | P0 | Integration | deadline 후 실행 | 1회 처리 |
| TIME-003 | P0 | Integration | 잘못된 배치 상태 | TurnSnapshot 복원 |
| TIME-004 | P0 | Integration | 유효 일부 배치 미확정 | 모두 복원 |
| TIME-005 | P0 | Integration | 조커 이동 상태 | 원래 위치 복원 |
| TIME-006 | P0 | Integration | SPEED 점수 임시 변화 | 복원 |
| TIME-007 | P0 | Integration | 풀 남음 | 자동 드로우 정확히 1회 |
| TIME-008 | P0 | Integration | 풀 비음 | PASS |
| TIME-009 | P0 | Integration | 동일 timeout 재실행 | 무시 |
| TIME-010 | P0 | WebSocket | 자동 드로우 타일 원문 | 당사자에게만 전송 |
| TIME-011 | P1 | E2E | 시간 초과 전체 흐름 | 모든 브라우저 상태 일치 |

---

# 14. SPEED 전체 제한시간

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| SPEED-001 | P2 | Integration | 5분 시작 | gameDeadline 설정 |
| SPEED-002 | P2 | Integration | 전체 종료 시 Draft 존재 | 롤백 |
| SPEED-003 | P2 | Integration | 전체 종료 | 추가 드로우 없음 |
| SPEED-004 | P2 | Unit | 기여 72, 잔여 20 | 최종 52 |
| SPEED-005 | P2 | Unit | 단독 최고 점수 | WIN |
| SPEED-006 | P2 | Unit | 최고 점수 동점 | DRAW |
| SPEED-007 | P2 | Integration | 종료 후 랭킹 | 변화 없음 |
| SPEED-008 | P2 | Integration | 전체 deadline과 턴 deadline 충돌 | 전체 종료 우선 |
| SPEED-009 | P2 | E2E | 실제 5분 경기 | 결과 화면 정상 |

---

# 15. 승리 및 교착

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| END-001 | P1 | Integration | 유효 확정 후 손패 0 | 즉시 WIN |
| END-002 | P0 | Integration | 무효 배치로 손패 0처럼 보임 | 승리 불가 |
| END-003 | P1 | Unit | 풀 고갈+전원 PASS | 교착 종료 |
| END-004 | P1 | Unit | 최저 손패 점수 단독 | WIN |
| END-005 | P1 | Unit | 최저 점수 동점 | DRAW |
| END-006 | P0 | Integration | 게임 종료 이벤트 재실행 | 결과 1회만 저장 |
| END-007 | P0 | Integration | 종료 후 행동 | GAME_ALREADY_FINISHED |
| END-008 | P1 | API | 결과 조회 | 참가자별 결과 정확 |

---

# 16. WebSocket 공개/개인 정보

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| WS-001 | P0 | WebSocket | 공개 GameState | rackCount만 존재 |
| WS-002 | P0 | WebSocket | 개인 GameState | 자기 rack만 존재 |
| WS-003 | P0 | E2E | 개발자도구 메시지 확인 | 상대 tileId 없음 |
| WS-004 | P0 | WebSocket | 드로우 공개 이벤트 | 타일 값 없음 |
| WS-005 | P0 | WebSocket | 드로우 개인 이벤트 | 해당 타일 포함 |
| WS-006 | P1 | WebSocket | 오류 응답 | 요청자 개인 채널 |
| WS-007 | P1 | WebSocket | PLAYER_DISCONNECTED | 공개 상태만 변경 |
| WS-008 | P1 | WebSocket | 재접속 | 최신 개인 상태 수신 |

---

# 17. actionId 및 gameVersion

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| CONC-001 | P0 | Integration | 동일 actionId 2회 | 상태 변경 1회 |
| CONC-002 | P0 | Integration | 동일 actionId 성공 결과 재요청 | 기존 결과 ACK |
| CONC-003 | P0 | Integration | 오래된 version | STALE_GAME_VERSION |
| CONC-004 | P0 | Integration | 현재 version | 정상 처리 |
| CONC-005 | P0 | Integration | 같은 게임 동시 확정 | 한 요청만 성공 |
| CONC-006 | P1 | Integration | 서로 다른 게임 동시 요청 | 병렬 처리 가능 |
| CONC-007 | P0 | Integration | version gap 이벤트 | 클라이언트 전체 재조회 |
| CONC-008 | P0 | DB | action_id UNIQUE | 중복 저장 실패 |

---

# 18. 재접속

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| REC-001 | P1 | API | 참가자 재접속 조회 | 성공 |
| REC-002 | P0 | API | 비참가자 재접속 조회 | 403 |
| REC-003 | P0 | API | 상대 손패 | 응답에 없음 |
| REC-004 | P1 | Integration | 새로고침 후 GamePlayer | 중복 생성 없음 |
| REC-005 | P1 | Integration | 오래된 Draft | 폐기 |
| REC-006 | P1 | WebSocket | 연결 상태 | DISCONNECTED→CONNECTED |
| REC-007 | P1 | E2E | 현재 턴 사용자 재접속 | 턴과 남은 시간 유지 |
| REC-008 | P1 | E2E | 비현재 턴 사용자 재접속 | 최신 테이블 복원 |

---

# 19. 자동 정렬 UX

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| SORT-001 | P3 | Front Unit | 777 정렬 | 숫자→색상 순 |
| SORT-002 | P3 | Front Unit | 789 정렬 | 색상→숫자 순 |
| SORT-003 | P3 | Front Unit | 중복 타일 | copy order 안정적 |
| SORT-004 | P3 | Front Unit | 조커 | 오른쪽 끝 |
| SORT-005 | P3 | E2E | 정렬 버튼 | 서버 요청 없음 |
| SORT-006 | P3 | E2E | 다른 플레이어 턴 | 정렬 가능 |
| SORT-007 | P3 | Front Unit | 원래 순서 | 마지막 서버 수신 순서 복원 |

---

# 20. 랭킹

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| RATE-001 | P2 | Unit | 신규 사용자 | 1000 |
| RATE-002 | P2 | Unit | 1000 vs 평균 800 승리 | 낮은 획득량 |
| RATE-003 | P2 | Unit | 1000 vs 평균 1000 승리 | 보통 획득량 |
| RATE-004 | P2 | Unit | 1000 vs 평균 1200 승리 | 높은 획득량 |
| RATE-005 | P2 | Unit | 낮은 상대에게 패배 | 큰 하락 |
| RATE-006 | P2 | Unit | 높은 상대에게 패배 | 작은 하락 |
| RATE-007 | P2 | Unit | DRAW | actualScore 0.5 |
| RATE-008 | P2 | Unit | 4인 상대 평균 | 자신 제외 평균 정확 |
| RATE-009 | P0 | Integration | 같은 게임 재반영 | 차단 |
| RATE-010 | P2 | Integration | rating 0에서 하락 | 0 미만 금지 |
| RATE-011 | P2 | Integration | SPEED 종료 | 랭킹 이력 없음 |
| RATE-012 | P2 | DB | game_id,user_id UNIQUE | 중복 이력 차단 |

---

# 21. 상대 전적

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| H2H-001 | P2 | Integration | A가 B에게 승리 | A WIN, B LOSS |
| H2H-002 | P2 | Integration | DRAW | 양쪽 DRAW |
| H2H-003 | P2 | Integration | CLASSIC 경기 | CLASSIC 집계 |
| H2H-004 | P2 | Integration | SPEED 경기 | SPEED 집계 |
| H2H-005 | P2 | Integration | 4인 경기 | 모든 참가자 쌍 갱신 |
| H2H-006 | P0 | Integration | 종료 이벤트 재전송 | 한 번만 증가 |
| H2H-007 | P2 | API | 특정 상대 조회 | 모드별 전적 정확 |
| H2H-008 | P2 | API | 최근 경기 | 시간 역순 |
| H2H-009 | P2 | DB | 동일 user/opponent/mode | 행 1개 |
| H2H-010 | P2 | Unit | 승률 | wins/totalGames |

---

# 22. DB 무결성

| ID | 우선순위 | 유형 | 시나리오 | 기대 결과 |
|---|---|---|---|---|
| DB-001 | P0 | DB | users.email 중복 | 차단 |
| DB-002 | P0 | DB | users.nickname 중복 | 차단 |
| DB-003 | P0 | DB | room player 중복 | 차단 |
| DB-004 | P0 | DB | room seat 중복 | 차단 |
| DB-005 | P0 | DB | game player 중복 | 차단 |
| DB-006 | P0 | DB | game result 중복 | 차단 |
| DB-007 | P0 | DB | rating history 중복 | 차단 |
| DB-008 | P0 | DB | rating 음수 | 차단 |
| DB-009 | P1 | DB | maxPlayers 1/5 | 차단 |
| DB-010 | P1 | DB | head-to-head 자기 자신 | 차단 |

---

# 23. 실제 E2E 시나리오

## E2E-001 2인 CLASSIC 기본 루프

```text
U1 로그인
→ 2인 CLASSIC 방 생성
→ U2 입장
→ 둘 다 준비
→ 게임 시작
→ 각자 손패 확인
→ 첫 등록
→ 드로우
→ 재조합
→ 손패 0개
→ 결과
→ 랭킹 반영
→ 상대 전적 반영
```

통과 기준:

- 상대 손패 미노출
- 결과·랭킹·전적 일치
- 에러 없음

## E2E-002 4인 CLASSIC

```text
4개 브라우저
→ 4인 입장
→ 턴 순환 확인
→ 동시 확정 시도
→ 한 요청만 성공
→ 게임 종료
```

## E2E-003 잘못된 배치 시간 초과

```text
턴 시작
→ 기존 조합 분리
→ 잘못된 상태 유지
→ 시간 초과
→ 원상복구
→ 자동 드로우
→ 다음 턴
```

증거:

- 시간 초과 전후 스크린샷
- 서버 로그
- 모든 브라우저 상태
- 타일 총량 검사 결과

## E2E-004 재접속

```text
게임 중 새로고침
→ active-session 감지
→ reconnect API
→ WebSocket 재구독
→ 자기 손패 복원
```

## E2E-005 SPEED

```text
2~4인 SPEED 시작
→ 5분 진행
→ 현재 Draft 남은 상태로 종료
→ Draft 롤백
→ 점수 계산
→ 동점 시 DRAW
→ 랭킹 미반영
```

---

# 24. 포트폴리오 증거 체크리스트

각 핵심 테스트 완료 시 저장한다.

```text
[ ] 테스트 ID
[ ] 테스트 목적
[ ] 환경
[ ] 재현 순서
[ ] 기대 결과
[ ] 실제 결과
[ ] 서버 로그
[ ] 화면 캡처
[ ] 실패 시 원인
[ ] 수정 파일
[ ] 회귀 테스트 결과
[ ] 내가 이해한 흐름
```

필수 영상:

- 2인 CLASSIC 전체 흐름
- 4인 동시접속
- 상대 손패 비공개
- 중복 확정 방지
- 잘못된 배치 시간 초과 롤백
- 재접속
- SPEED 5분 종료
- 랭킹 변동
- 특정 상대 전적

---

# 25. 완료 판정 기준

## 규칙 엔진 완료

- RUN/GROUP/첫 등록/조커/재조합 Unit Test 전체 통과
- 실패 시 상태 불변

## 실시간 게임 완료

- 2~4인 E2E 통과
- 공개·개인 채널 분리
- actionId/gameVersion 검증
- 시간 초과·재접속 통과

## CLASSIC 완료

- 정상 승리
- 교착 승리
- DRAW
- 결과·랭킹·전적 반영

## SPEED 완료

- 5분 제한
- 20초 턴
- 점수 계산
- DRAW
- 랭킹 미반영

---

# 26. 구현 전 최종 결정 필요 항목

다음 두 규칙은 코드 구현 전에 최종 고정해야 한다.

## 조커만으로 이루어진 조합

```text
JOKER, JOKER, JOKER
```

권장:

```text
실패
```

이유:

- 색상·숫자 역할이 완전히 불명확
- 자동 판정이 임의적
- 사용자에게 예측 가능성이 낮음

## 다인전 DRAW 범위

현재 확정안:

```text
최종 승자를 단독으로 정할 수 없으면 게임 전체 DRAW
```

후속 확장으로 일부 참가자 공동 순위가 필요하면 결과 모델을 확장한다.

---

# 27. 다음 문서

다음 단계:

1. 구현 단계별 작업지시서
2. 새 프로젝트 시작 프롬프트
3. 최종 기획 문서 인덱스
4. 개발 착수 패키지 ZIP

---

# 28. Phase 7 최소 게임 루프 테스트

| ID | 시나리오 | 기대 결과 |
|---|---|---|
| P7-COMMIT-001 | RED 789 + BLUE 123 첫 등록 | 합계 30, 성공 |
| P7-COMMIT-002 | 첫 등록 30점 미만 | 전체 롤백 |
| P7-COMMIT-003 | initial 완료 후 30점 미만 유효 Meld | 성공 |
| P7-COMMIT-004 | invalid/duplicate/foreign/POOL/TABLE tile | 거부, 상태 불변 |
| P7-COMMIT-005 | stale `gameVersion` | 거부, 최신 상태 복구 |
| P7-COMMIT-006 | 동일 `actionId` | 결과 replay, 재실행 없음 |
| P7-COMMIT-007 | COMMIT과 DRAW 경합 | 하나의 권위 변경만 성공 |
| P7-SEC-001 | 비참가자 `/turn/commit` | `GAME_MEMBERSHIP_REQUIRED` |
| P7-DRAFT-001 | Rack에서 새 Draft Meld | partition 보존 |
| P7-DRAFT-002 | 편집/Undo/Cancel | 서버 Rack 불변 |
| P7-HOLD-001 | 789/777 그룹 320ms Hold | 복수 타일 Overlay |
| P7-HOLD-002 | 6px 조기 이동 | 단일 드래그 유지 |
| P7-SYNC-001 | 공개 이벤트가 개인 Snapshot보다 선행 | Draft 조기 삭제 없음 |
| P7-SYNC-002 | 새로고침·다른 계정 | Table 유지, Private Rack 분리 |

2026-07-18 결과: Backend 290/290, Frontend 167/167, MySQL 8.4 V5, 2계정 Browser Runtime 통과.

---

# 29. Phase 7 Second Review 테스트

| ID 범위 | 시나리오 | 결과 |
|---|---|---|
| RECOMPOSE-001~024 | 연장·분리·병합·이동·거부·첫 등록 lock·rollback | Backend 통과 |
| LAYOUT-001~006 | 21~30장 Adaptive Rack | Frontend 통과 |
| TURN-UX-001~004 | 내 턴 Green Border/Badge | Frontend 통과 |
| STATUS-001~005 | 첫 등록/완료/이번 제출 점수 | Frontend 통과 |
| WORK-001~014 | Unified Working Table 편집 | Frontend 통과 |
| RECOVER-001~008 | Cancel·turn end·stale·reconnect | Frontend 통과 |
| COMMIT-FE-011~015 | 전체 Candidate 계약·동기화 | Frontend 통과 |

2026-07-18 Second Review 결과: Backend 296/296, Frontend 209/209, TypeScript/Production Build 통과. 인증된 게임 화면 수동 Runtime은 미실행이며 자동 테스트 결과와 분리한다.
