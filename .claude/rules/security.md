---
paths:
  - "src/main/java/com/demo/bestorytellers/auth/*.java"
  - "src/main/java/com/demo/bestorytellers/auth/**/*.java"
  - "src/main/java/com/demo/bestorytellers/config/*.java"
---

# Security Rules

## JWT

- JWT secret minimum 256-bit — always read from `${app.jwt.secret}`, never hardcode
- Access token expiry: 15 minutes
- Refresh token expiry: 7 days, stored in Redis under `session:{userId}`
- On logout: add `jti` to Redis blacklist `jwt:blacklist:{jti}` with TTL = remaining token lifetime
- JWT filter checks blacklist on every request before allowing through
- Never log full JWT tokens — log only the `jti` claim for tracing

## Authorization

- Admin-only endpoints: `@PreAuthorize("hasRole('ADMIN')")`
- Author-only actions: check `role == AUTHOR` in service, throw `ForbiddenException` if not
- Ownership checks always in service layer — never rely on security config alone:
  ```java
  if (!story.getAuthor().getId().equals(currentUserId)) {
      throw new ForbiddenException("You do not own this story");
  }
  ```

## Rate Limiting

- Auth endpoints (`/api/v1/auth/**`): 10 requests/min per IP
- Write endpoints (POST, PATCH, DELETE): 60 requests/min per authenticated user
- Rate limit keys: `ratelimit:{ip}:{endpoint}`, TTL 1 min

## Input Sanitization

- All story and chapter `content`: sanitize with jsoup before persist:
  ```java
  Jsoup.clean(content, Safelist.basicWithImages())
  ```
- Never trust client-supplied HTML — strip script, iframe, object, embed tags always

## CORS

- Allow only configured origin from `${app.cors.allowed-origin}`
- Never use `allowedOrigins("*")` in production config