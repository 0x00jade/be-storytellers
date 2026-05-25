---
paths:
  - "src/main/java/com/demo/bestorytellers/**/entity/*.java"
---

# Entity Rules

- Use UUID primary keys: `@GeneratedValue(strategy = GenerationType.UUID)`
- All entities extend `BaseEntity` which provides `createdAt` and `updatedAt` managed by `@PrePersist` and `@PreUpdate`
- Use `@Column(nullable = false)` for every required field — must match Flyway DDL
- Never use `EAGER` fetch — always `LAZY` on all `@OneToMany`, `@ManyToOne`, `@ManyToMany`
- Use `@Enumerated(EnumType.STRING)` always — never `EnumType.ORDINAL`
- Use `@Column(columnDefinition = "TIMESTAMPTZ")` for all timestamp fields
- Use `@Column(columnDefinition = "jsonb")` for JSONB fields, mapped as `String` in Java
- Use Hibernate `@Filter` for soft deletes where applicable — never hard delete rows that have dependents
- Never add business logic inside entities — no service calls, no computed mutations