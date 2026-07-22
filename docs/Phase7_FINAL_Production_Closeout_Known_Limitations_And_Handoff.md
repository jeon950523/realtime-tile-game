# Phase 7 FINAL Production Closeout — Known Limitations And Handoff

## 1. 현재 판정

```text
Renderer Decision: CLOSED
Production Renderer: HTML5 DOM
Automatic Frontend Verification: PASS
Backend Changed-Class Harness: PASS
Backend Full Maven Test: USER ENVIRONMENT REQUIRED
Browser Runtime: USER ENVIRONMENT REQUIRED
Phase 7 Status: FINAL Candidate
```

## 2. 알려진 제한

### 18행 상한

Table은 무한 Canvas가 아니라 18×18 논리 공간이다.

일반적인 게임은 이 범위 전에 종료될 가능성이 높으며, 현재 Closeout에서는 무한 좌표·Virtual Canvas를 도입하지 않는다.

18행을 전부 사용해 더 이상 유효한 Block을 배치할 수 없으면 Drop이 거부된다.

### HTML5 Drag

Production은 HTML5 Drag를 유지한다.

```text
데스크톱 마우스 중심
Native Pointer 전환 없음
Konva 전환 없음
모바일 Touch 최적화는 범위 밖
```

### Committed 표시 좌표

과거 서버 좌표가 유효하더라도 화면의 Committed Table은 Full Flow로 표시한다. 다음 Commit이 발생하면 Client Candidate의 정돈된 좌표가 서버 권위 검증을 거쳐 저장될 수 있다.

### Backend 전체 Test

현재 작업 Container는 Maven Wrapper Distribution을 내려받을 네트워크가 없어 전체 Backend Test를 실행하지 못했다.

사용자 PC에서 반드시:

```powershell
cd backend
.\mvnw.cmd test
```

를 통과해야 한다.

### 실제 Browser Runtime

DOM Unit Test는 Scroll·Auto-scroll·Nudge 계약을 확인하지만, 실제 화면 크기·브라우저 Drag·WebSocket 동기화는 Windows Runtime 검증을 대체하지 않는다.

## 3. 더 하지 않을 작업

Phase 7을 닫기 위해 다음을 새로 시작하지 않는다.

```text
새 Renderer Spike
Native Pointer 추가 최적화
Konva 재평가
무한 Canvas
모바일 Touch 전환
관전자
AI Player
재대결
3·4인 이탈 후 계속 진행
```

치명적인 Runtime 오류가 없다면 미세한 간격·색상·애니메이션은 Known Limitation으로 남기고 종료한다.

## 4. 다음 작업 순서

```text
1. Verify-Phase7FinalCandidate.ps1 실행
2. Kubernetes 새 이미지 배포
3. 2계정 첫 등록 전/후 검증
4. 첫 등록 후 기존 Meld 재조합 검증
5. 혼잡 Table Scroll/Auto-scroll 검증
6. 게임 포기 후 새 방 검증
7. Refresh/Reconnect 검증
8. Console 0 확인
9. Phase 7 FINAL 선언
10. 기능 확장 중단 후 포트폴리오·학습·다른 프로젝트로 이동
```

## 5. 최종 Runtime 보고 형식

사용자는 다음 형태로 결과를 남기면 된다.

```text
Backend tests:
Frontend tests:
TypeScript:
Production Build:
2-account initial meld:
Post-initial table rearrangement:
Rack tile to existing meld:
Undo / Cancel:
Crowded table scroll / auto-scroll:
Exit and new room:
Refresh / Reconnect:
Console error / warning:
```

모두 PASS이면 Phase 7 FINAL로 닫는다.
