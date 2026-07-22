# Backend

Phase 0 Spring Boot backend foundation.

## Run

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Test

```bash
./mvnw test
```

## Public probes

- REST: `GET http://localhost:8080/api/health`
- STOMP endpoint: `ws://localhost:8080/ws`
- Publish: `/app/system.health.ping`
- Subscribe: `/topic/system.health`
