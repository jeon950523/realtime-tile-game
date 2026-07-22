# Phase 7 Test Case Traceability

| 요구사항 | 자동 테스트 | Runtime 증거 |
|---|---|---|
| V5 Meld/Tile 제약 | `GamePersistenceConstraintIntegrationTest` | MySQL Flyway V5 성공 |
| 정확히 30점 첫 등록 | `GameTurnCommitIntegrationTest` | RED 789 24 + BLUE 123 6 COMMIT |
| 기존 Rule Engine 사용 | `TurnCommitValidatorTest`, commit integration | 서버 저장 타입·점수 24/6 |
| 30점 미만 롤백 | `GameTurnCommitIntegrationTest` | DB 상태 불변 테스트 |
| invalid/duplicate/foreign tile 거부 | `GameTurnCommitIntegrationTest` | 실패 응답 경계 확인 |
| actionId replay | `GameMessageControllerTest` | 동일 요청 한 번만 실행 |
| STOMP 참가자 권한 | `StompRoomSecurityIntegrationTest` | 실제 COMMIT CONNECTED 유지 |
| COMMIT/DRAW 경합 | `GameTurnCommitIntegrationTest` | 비관적 잠금 통합 테스트 |
| typed `tableMelds` | `Phase7CommitFrontend.spec.ts` | 새로고침 후 2 Meld 렌더링 |
| TurnDraft partition | `TurnDraft.spec.ts` | Rack 14 = 표시 8 + Draft 6 |
| Draft 편집/Undo/Cancel | `TurnDraft.spec.ts` | UI 컨트롤 활성 상태 확인 |
| RUN/GROUP 시각 그룹 | `RackVisualGroups.spec.ts` | 789 정렬에서 Hold 3장 |
| Hold/이동 취소/정리 | `RackGroupHold.spec.ts` | 실제 320ms Hold Drag |
| Phase 6 Motion 보존 | `RackMotionPolish.spec.ts`, `RackPresentation.spec.ts` | jitter 없는 fixed slot 이동 |
| 공개 이벤트 선행 경합 | `TurnDraft.spec.ts`, `Phase7CommitFrontend.spec.ts` | 개인 sync 뒤 Draft 제거 |
| 모든 사용자 동기화 | store/frontend tests | B COMMIT 후 A 로그인 Table·Rack 확인 |

최종 실행 결과:

```text
Backend clean test: 290/290 PASS
Frontend check: 167/167 PASS, 21/21 files
Type-check: PASS
Production build: PASS
MySQL 8.4.10 V5: PASS
Browser two-account runtime: PASS
```

