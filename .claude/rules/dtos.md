---
paths:
  - "src/main/java/com/demo/bestorytellers/**/dto/*.java"
---

# DTO Rules

- Use Java records for all DTOs:
  ```java
  public record StoryResponse(UUID id, String title, String slug, String status) {}
  ```
- Strict separation — never reuse the same class as both request and response:
    - Request DTOs: `CreateStoryRequest`, `UpdateStoryRequest`
    - Response DTOs: `StoryResponse`, `StoryDetailResponse`
- All request DTOs must have Bean Validation annotations:
    - `@NotBlank` for required strings
    - `@Size(min = n, max = n)` for length constraints
    - `@Email` for email fields
    - `@NotNull` for required objects
- Use `@Valid` on every `@RequestBody` parameter in controllers
- Never expose internal fields (passwords, provider_id, raw tokens) in response DTOs
- Use `PageResponse<T>` for all paginated responses — never return raw `Page<T>` to the client