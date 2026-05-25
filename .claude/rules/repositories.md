---
paths:
  - "src/main/java/com/demo/bestorytellers/**/repository/*.java"
---

# Repository Rules

- Extend `JpaRepository<Entity, UUID>` for all repositories
- Use `@Query` with JPQL for complex queries — native SQL only when JPQL cannot express it
- All public-facing listing methods must return `Page<T>`, never `List<T>`:
  ```java
  Page<Story> findByStatusAndVisibility(String status, String visibility, Pageable pageable);
  ```
- Full-text search must use PostgreSQL tsvector:
  ```java
  @Query(value = "SELECT * FROM stories WHERE search_vector @@ plainto_tsquery('english', :query)", nativeQuery = true)
  Page<Story> searchByText(@Param("query") String query, Pageable pageable);
  ```
- Add `countQuery` to all `@Query` pagination methods to avoid slow count:
  ```java
  @Query(value = "SELECT s FROM Story s WHERE ...",
         countQuery = "SELECT COUNT(s) FROM Story s WHERE ...")
  ```
- Never call repository methods directly from controllers — always go through service