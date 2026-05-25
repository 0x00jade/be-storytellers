---
paths:
  - "src/main/java/com/demo/bestorytellers/**/service/*.java"
  - "src/main/java/com/demo/bestorytellers/**/controller/*.java"
  - "src/main/java/com/demo/bestorytellers/common/exception/*.java"
---

# Exception Rules

## Exception → HTTP Status Mapping

| Exception                   | HTTP Status | When to throw                                        |
|-----------------------------|-------------|------------------------------------------------------|
| `ResourceNotFoundException` | 404         | Entity not found by ID or slug                       |
| `ForbiddenException`        | 403         | Authenticated user does not own the resource         |
| `ValidationException`       | 400         | Business rule violation (not Bean Validation)        |
| `UnauthorizedException`     | 401         | Token missing, expired, or blacklisted               |
| `ConflictException`         | 409         | Duplicate slug, already following, already voted     |
| `RateLimitException`        | 429         | Too many requests                                    |

## Rules

- All exceptions handled centrally in `GlobalExceptionHandler extends ResponseEntityExceptionHandler`
- Never throw raw `RuntimeException` or `Exception` from service layer
- Never catch and swallow exceptions silently — always log or rethrow
- Error response always uses `ApiResponse.error(message)` shape:
  ```json
  { "success": false, "data": null, "message": "Story not found: my-slug", "code": "STORY_NOT_FOUND" }
  ```
- Exception messages must include the identifier:
  ```java
  throw new ResourceNotFoundException("Story not found: " + slug);
  ```
- Never expose stack traces or internal class names in error responses