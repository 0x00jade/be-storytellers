---
paths:
  - "src/main/resources/db/migration/*.sql"
  - "src/main/java/com/demo/bestorytellers/**/entity/*.java"
  - "src/main/java/com/demo/bestorytellers/**/repository/*.java"
---

# Database & Flyway Rules

## Migration Files

- Location: `src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql` — e.g. `V1__init.sql`, `V2__add_story_tags.sql`
- Version numbers are sequential integers — never reuse or skip
- Never modify an already-applied migration file — create a new version instead
- Always additive: add columns as `NULL` or with a `DEFAULT` — never add `NOT NULL` without a default to an existing table
- Never drop or rename a column in a single migration — deprecate first, remove in a later version

## Schema Rules

- Use `TIMESTAMPTZ` for all timestamp columns — never plain `TIMESTAMP`
- Use `UUID` with `DEFAULT gen_random_uuid()` for all primary keys
- Use `VARCHAR(n)` with explicit length — never bare `VARCHAR`
- Use `TEXT` for unbounded strings (content, bio, URLs)
- All enum-like columns: `VARCHAR(20) NOT NULL` with a `CHECK` constraint listing valid values

## Index Rules

Add an index for every:
- Foreign key column
- Column used in `WHERE` clauses
- Column used in `ORDER BY` on large tables
- Full-text search column: `USING GIN(search_vector)`

```sql
CREATE INDEX idx_stories_author     ON stories(author_id);
CREATE INDEX idx_stories_status     ON stories(status);
CREATE INDEX idx_stories_visibility ON stories(visibility, status);
CREATE INDEX idx_stories_search     ON stories USING GIN(search_vector);
```

## Hibernate Settings (never change)

```yaml
spring:
  jpa:
    hibernate.ddl-auto: validate   # Flyway owns the schema
    open-in-view: false            # Always false
```