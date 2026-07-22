# Phase 5.5-A Health WebSocket / Console Fix

## 변경 파일

```text
frontend/src/realtime/systemHealthClient.ts
frontend/src/router/index.ts
frontend/src/__tests__/SystemHealthClient.spec.ts
frontend/src/__tests__/RouterAuthenticationGuard.spec.ts
```

## 수정 내용

1. Production에서도 STOMP `debug`를 항상 호출 가능한 함수로 설정
2. 공개 `/health` 경로에서는 불필요한 Refresh Session 복구 요청 생략
3. STOMP debug 함수 회귀 테스트 추가
4. `/health`에서 세션 복구를 호출하지 않는 Router Guard 테스트 추가

## 검증 결과

```text
TypeScript 통과
Test Files 14 passed
Tests 99 passed
Production Build 통과
```
