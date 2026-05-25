---
paths:
- "src/test/java/com/demo/bestorytellers/**"
---


# Testing rules
- Unit tests: mock all dependencies with Mockito
- Integration tests: use `@SpringBootTest` + Testcontainers (PostgreSQL + Redis)
- Test naming: `methodName_whenCondition_thenExpectedResult`
- Every new service method needs at minimum: happy path test + error case test
- Repository custom queries need integration test with real DB via Testcontainers
