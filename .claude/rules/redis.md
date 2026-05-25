---
paths:
  - "src/main/java/com/demo/bestorytellers/**/service/*.java"
  - "src/main/java/com/demo/bestorytellers/config/*.java"
---

# Redis Rules

## Key Naming Convention

Always follow `{domain}:{identifier}` pattern. Never freeform key names.

| Key Pattern                      | Type        | TTL      | Purpose                        |
|----------------------------------|-------------|----------|--------------------------------|
| `jwt:blacklist:{jti}`            | String      | Token TTL | Invalidated JWT tokens        |
| `session:{userId}`               | String      | 7 days   | Refresh token store            |
| `story:views:{storyId}`          | String      | 5 min    | Buffered view counter          |
| `story:hot`                      | Sorted Set  | 15 min   | Trending stories               |
| `story:{slug}`                   | String/JSON | 10 min   | Cached story detail            |
| `user:{username}`                | String/JSON | 10 min   | Cached user profile            |
| `feed:{userId}`                  | String/JSON | 5 min    | Cached personalized feed       |
| `ratelimit:{ip}:{endpoint}`      | String      | 1 min    | Rate limiter token bucket      |
| `chapter:{chapterId}` | 30 min | Published Delta JSON fetched from S3 |
| `draft:{chapterId}:{userId}` | 24h | Autosave existence flag (real content in S3) |

## Code Rules

- Always set TTL on every key — never store without expiry
- Use `RedisTemplate<String, String>` for simple string/counter values
- Use `RedisTemplate<String, Object>` for serialized objects
- On cache miss: load from DB, write to Redis, return result — never return null from a cached method
- Invalidate cache on every write: delete `story:{slug}` when story is updated
- View count flush pattern:
  ```java
  redisTemplate.opsForValue().increment("story:views:" + storyId);
  // Scheduled job every 5 min: read all story:views:* keys → batch UPDATE stories
  ```
- Never store sensitive data (tokens, passwords) without TTL
- draft:{chapterId}:{userId} stores "exists" string only — never store full Delta JSON in Redis (too large)
- On cache miss for chapter:{chapterId}: fetch from S3, then cache — never fetch from DB