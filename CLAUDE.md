# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Project: BE-StoryTellers

## Build & Run Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew test --tests "com.demo.bestorytellers.SomeTest#methodName"  # single test
./gradlew bootRun        # start dev server (requires Postgres + Redis running)
./gradlew clean build    # clean build
./gradlew test --tests "*IntegrationTest"  # integration tests only
./gradlew flywayMigrate  # run pending DB migrations
./gradlew bootJar        # build production JAR
docker compose up -d postgres redis  # start local dependencies
```

## Project Instructions

- Always use **Gradle** for dependency management
- Artifact name must match the parent directory name (`be-storytellers`)
- Use semantic versioning; bump PATCH in `build.gradle.kts` on each new version
- Do **not** use the Lombok library
- Always create test cases for generated code — both positive and negative
- Generate CircleCI pipeline in `.circleci/` to verify the code
- Generate `docker-compose.yml` covering all components (PostgreSQL, Redis)
- Update `README.md` on each new version
- Minimize the amount of code generated

## Tech Stack and Docs
@docs/system-design.md
@docs/erd.md
@docs/api-contracts.md
@docs/flow.md

## Package Structure

```
com.bestorytellers.
  auth/          - OAuth2, JWT, token management
  user/          - User profiles, follows
  story/         - Story CRUD, tags
  chapter/       - Chapter CRUD, publishing
  reading/       - Reading lists, progress
  social/        - Comments, votes
  search/        - Full-text search (PostgreSQL tsvector)
  notification/  - User notifications
  common/        - DTOs, exceptions, utils
  config/        - All @Configuration classes
```

Each module has: `controller/`, `service/`, `repository/`, `entity/`, `dto/`

## Common Patterns

### API Response Wrapper

```java
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

### Paginated Response

```java
public record PageResponse<T>(List<T> content, int page, int size, long total, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(),
            page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
```

### View Count (Redis buffered)

- Increment `story:views:{storyId}` in Redis on each chapter read
- Scheduled job every 5 min flushes Redis counts → PostgreSQL batch UPDATE

### Story Slug Generation

- Slug = `slugify(title)` + `-` + first 8 chars of UUID
- Use `SlugUtil.generate(title, id)` from `common/util/`

## Chapter Content (S3)
- Never store chapter content in PostgreSQL — always S3
- S3 keys:
    - Published: `content/{chapterId}/published.json`
    - Versions:  `content/{chapterId}/v{versionNumber}.json`
    - Autosave:  `drafts/{chapterId}/{userId}.json`
- Content format: Quill Delta JSON — validate structure before upload
- Word count: extract plain text from Delta ops, count whitespace-split tokens
- Use S3Util.uploadContent(key, deltaJson) and S3Util.fetchContent(key)
- Redis chapter:{chapterId} caches the raw Delta string, TTL 30 min
- draft:{chapterId}:{userId} is an existence flag only — actual draft content is in S3

## What NOT to Do

- Never use `@Autowired` on fields — constructor injection only
- Never put `@Transactional` on controllers
- Never return raw entity from controller
- Never use `ddl-auto: create` or `update` — Flyway only
- Never store JWT in localStorage — document this in API responses
- Never skip ownership check on mutating operations
- Never use `List<T>` for public paginated endpoints — always `Page<T>`
- Never log passwords, tokens, or full request bodies

## Process

### 1. Plan Mode Default

- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Self-Improvement Loop

- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until the mistake rate drops
- Review `tasks/lessons.md` at session start for this project

### 3. Verification Before Done

- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 4. Demand Elegance

- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes — don't overengineer

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Minimal code impact
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

## Update api-contract.md for FE
- Update ../fe-storytellers/api-contract.md everytime create or update an api for FE catchup change of BE