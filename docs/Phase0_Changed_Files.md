# Phase 0 생성·관리 파일 목록

작성 기준: 2026-07-14  
갱신 작업: Phase 0 Final Stabilization And Documentation Consistency Cleanup

아래 목록은 현재 Phase 0 프로젝트에 포함된 실제 파일의 상대 경로다. `node_modules`와 `dist` 같은 생성물은 결과물에 포함하지 않는다.

## Root

- `.env.example`
- `.gitignore`
- `README.md`
- `compose.yaml`

## Backend

- `backend/.gitignore`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/README.md`
- `backend/mvnw`
- `backend/mvnw.cmd`
- `backend/pom.xml`
- `backend/src/main/java/com/realtimetilegame/RealtimeTileGameApplication.java`
- `backend/src/main/java/com/realtimetilegame/common/api/ApiResponse.java`
- `backend/src/main/java/com/realtimetilegame/common/error/ApiErrorResponse.java`
- `backend/src/main/java/com/realtimetilegame/common/error/ErrorCode.java`
- `backend/src/main/java/com/realtimetilegame/common/error/GlobalExceptionHandler.java`
- `backend/src/main/java/com/realtimetilegame/common/error/ServiceUnavailableException.java`
- `backend/src/main/java/com/realtimetilegame/config/CorsProperties.java`
- `backend/src/main/java/com/realtimetilegame/config/JpaAuditingConfiguration.java`
- `backend/src/main/java/com/realtimetilegame/config/SecurityConfiguration.java`
- `backend/src/main/java/com/realtimetilegame/health/application/HealthService.java`
- `backend/src/main/java/com/realtimetilegame/health/presentation/HealthController.java`
- `backend/src/main/java/com/realtimetilegame/health/presentation/dto/HealthResponse.java`
- `backend/src/main/java/com/realtimetilegame/security/RestAccessDeniedHandler.java`
- `backend/src/main/java/com/realtimetilegame/security/RestAuthenticationEntryPoint.java`
- `backend/src/main/java/com/realtimetilegame/websocket/config/WebSocketConfiguration.java`
- `backend/src/main/java/com/realtimetilegame/websocket/presentation/SystemHealthMessageController.java`
- `backend/src/main/java/com/realtimetilegame/websocket/presentation/dto/RealtimeHealthMessage.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/.gitkeep`
- `backend/src/test/java/com/realtimetilegame/health/HealthApiIntegrationTest.java`
- `backend/src/test/java/com/realtimetilegame/websocket/WebSocketHealthIntegrationTest.java`
- `backend/src/test/resources/application-test.yml`

## Frontend

- `frontend/.editorconfig`
- `frontend/.env.example`
- `frontend/.env.test`
- `frontend/.gitattributes`
- `frontend/.gitignore`
- `frontend/.vscode/extensions.json`
- `frontend/.vscode/settings.json`
- `frontend/README.md`
- `frontend/env.d.ts`
- `frontend/index.html`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/public/favicon.ico`
- `frontend/src/App.vue`
- `frontend/src/__tests__/App.spec.ts`
- `frontend/src/__tests__/ConnectionStore.spec.ts`
- `frontend/src/__tests__/SystemHealthClient.spec.ts`
- `frontend/src/api/healthApi.ts`
- `frontend/src/api/httpClient.ts`
- `frontend/src/assets/main.css`
- `frontend/src/main.ts`
- `frontend/src/realtime/systemHealthClient.ts`
- `frontend/src/router/index.ts`
- `frontend/src/stores/connection.ts`
- `frontend/src/types/api.ts`
- `frontend/src/types/health.ts`
- `frontend/src/views/HealthView.vue`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.json`
- `frontend/tsconfig.node.json`
- `frontend/tsconfig.vitest.json`
- `frontend/vite.config.ts`
- `frontend/vitest.config.ts`

## Documents

- `docs/Phase0_Changed_Files.md`
- `docs/Phase0_Completion_Report.md`
- `docs/Phase0_Direct_Verification_Guide.md`
- `docs/Phase0_Document_Baseline_Summary.md`
- `docs/Phase0_Document_Consistency_Cleanup_Report.md`
- `docs/Phase0_Final_Stabilization_Report.md`
- `docs/specs/Realtime_Tile_Game_Document_Index_v1.md`
- `docs/specs/Realtime_Tile_Game_ERD_Table_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_Implementation_Roadmap_Task_v1.md`
- `docs/specs/Realtime_Tile_Game_Modes_Rating_Record_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_New_Project_Start_Prompt_v1.md`
- `docs/specs/Realtime_Tile_Game_Portfolio_Evidence_Log_v1.md`
- `docs/specs/Realtime_Tile_Game_Project_Development_Guidelines_v2.md`
- `docs/specs/Realtime_Tile_Game_Project_Planning_v1.md`
- `docs/specs/Realtime_Tile_Game_REST_API_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_Rule_Engine_Class_Design_v1.md`
- `docs/specs/Realtime_Tile_Game_Rules_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_SRS_v1.md`
- `docs/specs/Realtime_Tile_Game_Server_GameState_Model_v1.md`
- `docs/specs/Realtime_Tile_Game_Test_Case_Matrix_v1.md`
- `docs/specs/Realtime_Tile_Game_UI_Flow_Wireframe_v1.md`
- `docs/specs/Realtime_Tile_Game_WebSocket_Message_Spec_v1.md`

## 이번 최종 안정화 patch의 수정·생성 파일

- `README.md`
- `docs/Phase0_Changed_Files.md`
- `docs/Phase0_Completion_Report.md`
- `docs/Phase0_Direct_Verification_Guide.md`
- `docs/Phase0_Document_Baseline_Summary.md`
- `docs/Phase0_Document_Consistency_Cleanup_Report.md`
- `docs/Phase0_Final_Stabilization_Report.md`
- `docs/specs/Realtime_Tile_Game_Document_Index_v1.md`
- `docs/specs/Realtime_Tile_Game_New_Project_Start_Prompt_v1.md`
- `docs/specs/Realtime_Tile_Game_Project_Planning_v1.md`
- `docs/specs/Realtime_Tile_Game_Rules_Spec_v1.md`
- `docs/specs/Realtime_Tile_Game_SRS_v1.md`
- `frontend/package-lock.json`
- `frontend/src/__tests__/ConnectionStore.spec.ts`
- `frontend/src/__tests__/SystemHealthClient.spec.ts`
- `frontend/src/assets/main.css`
- `frontend/src/realtime/systemHealthClient.ts`
- `frontend/src/stores/connection.ts`
- `frontend/src/views/HealthView.vue`

## 범위 확인

- Backend 소스와 Backend WebSocket endpoint는 변경하지 않았다.
- Frontend는 수동 WebSocket 재연결 lifecycle, 중복 연결·구독 방지, 테스트와 lockfile만 수정했다.
- `docs/specs`는 이전 문서 정합성 보완에서 확인된 5개 원본 문서만 수정했다.
- Phase 1 규칙 엔진, JWT 실제 구현, 방·로비·GameState는 추가하지 않았다.
- 현재 관리 파일 수: 87개
- 원본 Phase 0 ZIP 대비 수정·생성 파일 수: 19개
