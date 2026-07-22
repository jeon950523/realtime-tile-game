# Verification Result

## Frontend

```text
TypeScript: PASS
Vitest: 31 files / 362 tests PASS
Production Build: PASS
Vite modules: 156
```

Batch 결과:

```text
11 files / 111 tests
7 files / 115 tests
7 files / 77 tests
6 files / 59 tests
합계 31 files / 362 tests
```

## Backend

변경 코드와 테스트를 작성했으나 현재 실행 환경은 Maven 배포 파일을 내려받을 수 없어 전체 Maven 테스트를 실행하지 못했다.

```text
repo.maven.apache.org DNS 접근 실패
```

사용자 Windows 환경에서 다음을 실행해야 한다.

```powershell
cd backend
.\mvnw.cmd test
```

추가 검증 항목:

- DB raw query로 terminal game 참가자의 `left_at IS NULL`이 0건인지 확인
- CLOSED room membership이 새 방 생성을 막지 않는지 확인
- `ROOM_CLOSED`, `ROOM_REMOVED` 이벤트 발행 확인
