# 실시간 타일 보드게임 문서 인덱스 v1

작성 기준: 2026-07-14  
상태: 기획 완료 / 개발 착수 가능

---

# 1. 프로젝트 개발 지침

## 1. `Realtime_Tile_Game_Project_Development_Guidelines_v2.md`

코드 품질, 정합성, 원자성, 확장성, 보드게임 플랫폼 방향, diff 기반 수정 원칙을 정의한다.

---

# 2. 프로젝트 개요

## 2. `Realtime_Tile_Game_Project_Planning_v1.md`

전체 프로젝트 방향, 목표, MVP 범위, 기술 스택, 구현 순서를 정리한 마스터 기획서.

---

# 3. 게임 규칙

## 3. `Realtime_Tile_Game_Rules_Spec_v1.md`

타일 구성, RUN, GROUP, 첫 등록 30점, 조커, 재조합, 시간 초과, CLASSIC/SPEED 규칙을 정의한다.

---

# 4. 화면 및 사용자 흐름

## 4. `Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`

랜딩, 회원가입, 로그인, 로비, 대기방, 게임, 결과, 재접속 화면 흐름과 와이어프레임을 정의한다.

---

# 5. 요구사항

## 5. `Realtime_Tile_Game_SRS_v1.md`

기능 요구사항, 비기능 요구사항, 인수 조건, 불변조건, 완료 기준을 식별자와 함께 정의한다.

---

# 6. 게임 모드·랭킹·전적

## 6. `Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`

CLASSIC, SPEED, DRAW, 랭킹 계산, 상대 전적 정책을 정의한다.

---

# 7. 데이터베이스

## 7. `Realtime_Tile_Game_ERD_Table_Spec_v1.md`

ERD, 테이블, 컬럼, 인덱스, 제약, 트랜잭션 경계를 정의한다.

---

# 8. REST API

## 8. `Realtime_Tile_Game_REST_API_Spec_v1.md`

인증, 프로필, 방, 재접속, 결과, 랭킹, 상대 전적 REST API를 정의한다.

---

# 9. WebSocket

## 9. `Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`

STOMP 목적지, 공개/개인 채널, 요청 Envelope, 이벤트, 중복 처리, 시간 초과와 재접속 메시지를 정의한다.

---

# 10. 서버 실시간 상태

## 10. `Realtime_Tile_Game_Server_GameState_Model_v1.md`

GameState, TurnState, TurnSnapshot, GameSession, 공개·개인 Snapshot, 동시성 모델을 정의한다.

---

# 11. 규칙 엔진

## 11. `Realtime_Tile_Game_Rule_Engine_Class_Design_v1.md`

RUN, GROUP, 첫 등록, 재조합, 조커, 타일 무결성, CLASSIC/SPEED 정책 클래스를 정의한다.

---

# 12. 테스트

## 12. `Realtime_Tile_Game_Test_Case_Matrix_v1.md`

Unit, Integration, API, WebSocket, E2E 테스트를 ID와 우선순위로 정리한다.

---

# 13. 구현 로드맵

## 13. `Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md`

Phase 0~15의 구현 순서, 작업 대상, 테스트, 완료 기준, 포트폴리오 증거를 정의한다.

---

# 14. 포트폴리오 기록

## 14. `Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md`

사용자 직접 기여, AI 작성 범위, 테스트, 버그, 회귀 검증, 면접 설명 내용을 기록한다.

---

# 15. 새 프로젝트 시작

## 15. `Realtime_Tile_Game_New_Project_Start_Prompt_v1.md`

새 코드 구현 프로젝트 채팅에 그대로 붙여넣을 시작 프롬프트다.

---

# 16. 문서 우선순위

## 개발자가 먼저 읽을 순서

```text
Project Planning
→ Rules
→ SRS
→ Implementation Roadmap
→ GameState
→ Rule Engine
→ REST
→ WebSocket
→ ERD
→ Test Matrix
```

## 프론트 작업 시

```text
UI Flow
→ SRS
→ REST
→ WebSocket
→ GameState Snapshot
```

## 백엔드 규칙 작업 시

```text
Rules
→ GameState
→ Rule Engine
→ Test Matrix
```

## 포트폴리오 정리 시

```text
Portfolio Evidence Log
→ SRS
→ Architecture documents
→ Test Matrix
→ Implementation Roadmap
```

---

# 17. 현재 확정 핵심

```text
인원: 2~4인

CLASSIC
- 첫 등록 30점
- 턴 기본 120초
- 손패 0개 승리
- 랭킹 반영

SPEED
- 전체 5분
- 턴 기본 20초
- 첫 등록 없음
- 점수 승부
- 랭킹 미반영

공통
- 최종 동점 DRAW
- 777·789 자동 정렬
- 상대 손패 비공개
- 시간 초과 시 배치 전 복원
- actionId 중복 방지
- gameVersion 충돌 방지
- 재접속 복원
```

---

# 18. 구현 시작점

```text
Phase 0 — 개발 환경과 프로젝트 뼈대
```

다음 단계는 코드 구현 전용 프로젝트에서 진행한다.


---

# 19. 상시 최우선 기준

새 구현 작업에서는 `Realtime_Tile_Game_Project_Development_Guidelines_v2.md`를 가장 먼저 읽고 적용한다.
