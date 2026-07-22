# Phase 5.5-A Changed Files

작성 기준: 2026-07-16 KST

기준 전체본:

```text
phase0716-00-31-phase5-final-clean-source-fixed.zip
```

최종 통합 전체본:

```text
phase0716-01-57-phase5_5A-final-clean-source.zip
```

작업:

```text
Phase 5.5-A — Minimum Dockerized Local Runtime
```

## 코드·설정 수정 파일

- `.env.example`
- `compose.yaml`
- `frontend/env.d.ts`
- `frontend/src/api/httpClient.ts`
- `frontend/src/realtime/authenticatedStompClient.ts`
- `frontend/src/realtime/systemHealthClient.ts`
- `frontend/src/router/index.ts`
- `frontend/src/views/HealthView.vue`
- `frontend/src/__tests__/SystemHealthClient.spec.ts`
- `frontend/src/__tests__/RouterAuthenticationGuard.spec.ts`

## 코드·설정 신규 파일

- `backend/Dockerfile`
- `backend/.dockerignore`
- `frontend/Dockerfile`
- `frontend/.dockerignore`
- `frontend/nginx/default.conf`
- `frontend/src/config/runtimeEndpoints.ts`
- `frontend/src/__tests__/RuntimeEndpoints.spec.ts`

## 문서 수정 파일

- `README.md`

## 문서 신규 파일

- `docs/Phase5_5A_Changed_Files.md`
- `docs/Phase5_5A_Completion_Report.md`
- `docs/Phase5_5A_Direct_Verification_Guide.md`
- `docs/Phase5_5A_Docker_Architecture_And_Operations.md`
- `docs/Phase5_5A_Health_WebSocket_Console_Fix_Changed_Files.md`

## 구현 중 추가 Fix

### Production STOMP Debug Callback

```text
증상:
Production Build에서 this.debug is not a function
→ WebSocket이 CONNECTING에 머묾

수정:
Production에서도 호출 가능한 No-op debug 함수 제공
```

### 공개 Health Route 인증 복구 제외

```text
증상:
DB 초기화 후 남은 Refresh Cookie 때문에
/health 접근 시 /api/auth/reissue 401 발생

수정:
공개 /health에서는 불필요한 Session Restore 생략
```

## Patch·클린 전체본 제외 확인

```text
.env
.env.local
frontend/.env.local
backend/target
frontend/node_modules
frontend/dist
frontend/coverage
.git
.idea
.vscode
로그
실제 JWT Secret
실제 DB Password
중첩 ZIP
```
