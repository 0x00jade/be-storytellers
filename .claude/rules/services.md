---
paths:
  - "src/main/java/com/demo/bestorytellers/**/service/*.java"
---

# Service Rules

- All read-only methods: `@Transactional(readOnly = true)`
- All write methods: `@Transactional`
- Never put `@Transactional` on controllers — services only
- Ownership check before any mutation:
  ```java
  if (!resource.getOwner().getId().equals(currentUserId)) {
      throw new ForbiddenException("You do not own this resource");
  }
  ```
- Never expose entities from service — always map to DTOs before returning
- Never return `List<T>` from methods that back public endpoints — return `Page<T>`
- Use constructor injection only — never `@Autowired` on fields
- Never throw raw `RuntimeException` — always use typed exceptions from `common/exception/`
- All async operations (notifications, cache flush) use `@Async` — never block the main thread
- Chapter content is always fetched from S3 via content_url — never stored in DB column
- Autosave uploads to S3 only — no DB write, no version record
- Manual save = S3 upload + DB update + version record, all in same @Transactional block
- Always delete S3 draft object after successful manual save
- Always invalidate chapter:{chapterId} cache after content write