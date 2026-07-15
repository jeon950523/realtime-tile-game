# 실시간 타일 보드게임 새 프로젝트 시작 프롬프트 v1

실시간 타일 보드게임 프로젝트 코드 구현 전용 채팅이다.

## 프로젝트 목표

학원 내부망에서 2~4명이 함께 플레이할 수 있는 실시간 숫자 타일 보드게임을 구현한다.

핵심 기능:

```text
회원가입·로그인
로비·방 생성·입장·준비
2~4인 실시간 플레이
RUN·GROUP
첫 등록 30점
조커
기존 테이블 재조합
777·789 손패 자동 정렬
턴 확정·취소
시간 초과 원상복구
재접속
CLASSIC 결과
상대 전적
랭킹
SPEED 5분 모드
```

## 기술 스택

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

## 반드시 먼저 읽을 문서

다음 순서로 문서를 읽고 기준을 이해한다.

```text
1. Realtime_Tile_Game_Project_Development_Guidelines_v2.md
2. Realtime_Tile_Game_Project_Planning_v1.md
3. Realtime_Tile_Game_Rules_Spec_v1.md
4. Realtime_Tile_Game_SRS_v1.md
5. Realtime_Tile_Game_UI_Flow_Wireframe_v1.md
6. Realtime_Tile_Game_ERD_Table_Spec_v1.md
7. Realtime_Tile_Game_REST_API_Spec_v1.md
8. Realtime_Tile_Game_WebSocket_Message_Spec_v1.md
9. Realtime_Tile_Game_Server_GameState_Model_v1.md
10. Realtime_Tile_Game_Rule_Engine_Class_Design_v1.md
11. Realtime_Tile_Game_Test_Case_Matrix_v1.md
12. Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md
13. Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md
14. Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md
15. Realtime_Tile_Game_Document_Index_v1.md
```

## 현재 시작 단계

```text
Phase 0 — 개발 환경과 프로젝트 뼈대
```

## Phase 0 목표

백엔드·프론트·DB·WebSocket의 기본 연결이 가능한 프로젝트 뼈대를 만든다.

### Backend

- Spring Boot 프로젝트
- Java 17
- MySQL
- JPA
- Validation
- Spring Security 기본 경계
- 401/403 공통 오류 계약
- JWT 적용을 위한 확장 위치
- Spring WebSocket/STOMP
- 공통 응답
- 공통 예외 처리
- Auditing
- `/api/health`

Phase 0에서는 JWT의 실제 발급·검증을 구현하지 않는다.

```text
Access Token 발급
JWT 인증 필터
Refresh Token
회원가입·로그인 인증 흐름
```

위 항목은 Phase 2 — 인증과 사용자 프로필에서 구현한다.

### Frontend

- Vue 3 + Vite
- Pinia
- Vue Router
- Axios
- STOMP Client
- 환경변수
- 기본 레이아웃
- `/health` 또는 연결 확인 화면

## 반드시 지킬 설계 원칙

```text
서버 권한형 게임 상태
상대 손패 원문 비공개
actionId 중복 방지
gameVersion 충돌 방지
Candidate 검증 후 Commit
TurnSnapshot 롤백
서버 기준 타이머
CLASSIC/SPEED 정책 분리
게임 결과·랭킹·전적 중복 반영 금지
```

## 금지

```text
상용 루미큐브 로고·이미지·UI 복제
클라이언트 판정만으로 상태 확정
상대 손패 브로드캐스트
검증 전 GameState 직접 수정
테스트 없이 완료 처리
무관한 리팩터링
후속 Phase 기능 선행 구현
```

## 코드 구현 운영 규칙

각 작업은 한 번에 한 Phase 또는 한 기능 단위로 진행한다.

사용자가 최신 전체 프로젝트 ZIP을 올리면:

1. 전체 구조를 확인한다.
2. 현재 Phase 기준으로 필요한 파일만 수정한다.
3. 범위 밖 수정은 하지 않는다.
4. 수정·생성 파일만 포함한 patch ZIP을 우선 제공한다.
5. 변경 파일 목록을 함께 제공한다.
6. 자동 테스트를 추가한다.
7. 사용자가 직접 확인할 실행 순서를 작성한다.
8. 완료 보고서 `.md`를 함께 제공한다.

## 검수 운영 규칙

사용자가 적용한 전체 프로젝트 ZIP과 테스트 결과를 올리면:

1. 현재 작업 목표와 일치하는지 검수한다.
2. 요구사항 누락을 확인한다.
3. 상태 중복·보안·원자성 위험을 확인한다.
4. 수정이 필요하면 patch ZIP과 보완 지시서를 만든다.
5. 통과라면 다음 Phase 작업지시서를 만든다.
6. 포트폴리오 증거 로그에 남길 내용을 함께 정리한다.

## 산출물 형식

코드 구현 결과:

```text
- patch ZIP
- 변경 파일 목록
- 자동 테스트 목록
- 직접 실행 절차
- 완료 보고서 md
```

문서 결과:

```text
- 개별 md
- 최신 전체 문서 ZIP
```

## 포트폴리오 원칙

생성형 AI가 코드를 작성하더라도 다음을 사용자가 직접 수행한 증거를 남긴다.

```text
기획
구조 결정
실행
DB 설정
테스트
오류 재현
로그 확인
수정 결과 검증
회귀 테스트
최종 품질 판단
핵심 코드 설명
```

AI 활용 표기:

```text
생성형 AI를 코드 작성 보조 도구로 활용했습니다.
기획, 아키텍처 결정, 실행 환경 구성, 테스트 시나리오 설계,
오류 재현, 수정 결과 검증 및 최종 품질 판단은 직접 수행했습니다.
```

## 첫 요청

현재 워크스페이스가 비어 있다면 Phase 0 프로젝트 뼈대를 만든다.

먼저 다음을 제공한다.

```text
1. Backend 프로젝트 구조
2. Frontend 프로젝트 구조
3. 필요한 의존성
4. application 설정 예시
5. 환경변수 예시
6. MySQL 초기 설정
7. health check
8. WebSocket 연결 확인
9. 실행 명령
10. Phase 0 테스트
```

한 번에 이후 Phase 기능을 구현하지 않는다.