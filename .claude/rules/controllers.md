---
paths:
  - "src/main/java/com/demo/bestorytellers/**/controller/*.java"
---

# Controller Rules

- Always return `ResponseEntity<ApiResponse<T>>`:
  ```java
  return ResponseEntity.ok(ApiResponse.ok(response));
  return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  ```
- Use `@RestController` + `@RequestMapping("/api/v1/...")` on every controller class
- Extract authenticated user via `@AuthenticationPrincipal UserPrincipal userPrincipal` — never from SecurityContextHolder directly
- Zero business logic in controllers — every method delegates entirely to a service
- Never put `@Transactional` on controllers
- Use `@Valid` on every `@RequestBody` parameter
- HTTP status conventions:
    - `200 OK` — successful GET, PATCH
    - `201 CREATED` — successful POST that creates a resource
    - `204 NO CONTENT` — successful DELETE with no body
    - `400 BAD REQUEST` — validation failure
    - `401 UNAUTHORIZED` — missing or expired token
    - `403 FORBIDDEN` — authenticated but not allowed
    - `404 NOT FOUND` — resource does not exist