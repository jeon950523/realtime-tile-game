# Phase 7 Third Review Test Traceability

## Backend

| ID | 검증 | 자동 테스트 |
|---|---|---|
| BE-P7-001,009 | 다른 플레이어 Meld 연장, 기존 creator 보존 | `GameTurnCommitIntegrationTest.beP7001And009_currentPlayerExtendsAnotherPlayersMeldAndPreservesCreator` |
| BE-P7-002~006,008 | 상대 Rack, POOL, 중복, TABLE 누락, invalid rollback, 다음 정상 Commit | `beP7002Through008_rejectsUnsafeCandidatesRollsBackAndAllowsTheNextValidCommit` 및 기존 Candidate rejection tests |
| BE-P7-007 | runtime failure도 actionId 포함 Reply | `GameMessageControllerTest.beP7007UnexpectedCommitFailureStillReturnsTheOriginalActionId` |
| BE-P7-010 | 새 Meld creator는 현재 Commit 사용자 | `beP7010_newMeldKeepsTheCurrentCommitPlayerAsCreator` |

## Frontend command lifecycle

`Phase7ThirdReviewFrontend.spec.ts`의 `FE-P7-001`부터 `FE-P7-012`가 필수 구독 준비, publish 실패, accepted/rejected Reply, Private State 유실, 9초 timeout, disconnect/STOMP error, route cleanup, duplicate Reply, 반영/미반영 REST recovery를 각각 검증한다.

`AuthenticatedStompClient.spec.ts`의 다음 기존·신규 테스트가 실제 subscription lifecycle을 보강한다.

- `gameReconnectLeavesOneActiveGameSubscription`
- `transportReconnectLeavesOneActiveReplySubscription`
- `reports game command readiness only after all required subscriptions exist`
- `notifies Pending recovery when the game WebSocket closes`

## Working Table UI

`Phase7ThirdReviewFrontend.spec.ts`의 `FE-P7-UI-001`부터 `FE-P7-UI-011`이 다음을 검증한다.

- per-tile/merge 버튼 미렌더링
- 같은 Meld 삽입, Meld 간 이동, 빈 공간 새 Meld
- Rack-origin 타일 Rack 반환과 TABLE-origin 반환 차단
- 빈 source compact
- Rack-only `전체 Rack`
- valid/invalid CSS와 긴 Meld markup

기존 `Phase7SecondReviewFrontend.spec.ts`의 `WORK-001`부터 `WORK-014`, `RECOVER-001`부터 `RECOVER-008`은 Working Table의 reorder/move/split/merge/Undo/Cancel/권위 복구 도메인 계약을 계속 검증한다.

## Joker

`Phase7ThirdReviewFrontend.spec.ts`의 `FE-P7-JOKER-001`부터 `FE-P7-JOKER-004`가 일반 RUN, RUN 중간·끝 Joker, GROUP Joker, 정확히 30점 첫 등록을 검증한다. invalid Meld의 점수는 기존 `validateTurnDraft` invalid tests와 `STATUS-005`에서 0점/Commit 차단으로 유지된다.

## 실행 결과

| 명령 | 결과 |
|---|---|
| `backend\\mvnw.cmd -Dtest=GameMessageControllerTest,GameTurnCommitIntegrationTest test` | 20 tests PASS |
| `backend\\mvnw.cmd clean verify` | 299 tests PASS, JAR PASS |
| `frontend\\npm run check` | 238 tests, typecheck, production build PASS |

