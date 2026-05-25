# System Design — StoryVerse (Wattpad-like Platform)

---

## 1. Final Tech Stack

```
Backend:        Java 21, Spring Boot 3.3, Gradle 8
Auth:           Spring Security 6 + OAuth2 Client (Google) + JWT (jjwt)
Database:       PostgreSQL 16 (primary), Flyway (migrations)
ORM:            Spring Data JPA + Hibernate
Cache:          Redis 7 (Lettuce client via Spring Data Redis)
Object Storage: AWS S3 (cover images, avatars)
Content Storage : AWS S3 (chapter content as Quill Delta JSON, version snapshots)
Content Format: Quill Delta JSON — rendered to HTML client-side
Search:         PostgreSQL full-text search (tsvector/tsquery)
API Docs:       SpringDoc OpenAPI 3 (Swagger UI at /swagger-ui)
Email:          Spring Mail + SendGrid
Rate Limiting:  Bucket4j + Redis
Containerization: Docker + Docker Compose
Testing:        JUnit 5, Mockito, Testcontainers
```

---

## 2. Architecture Overview

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Client (Web / Mobile)                 │
└────────────────────────┬────────────────────────────────┘
                         │ HTTPS
┌────────────────────────▼────────────────────────────────┐
│                   Spring Boot API                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ Auth Filter │  │ Rate Limiter│  │   Controllers   │ │
│  │  (JWT)      │  │ (Bucket4j)  │  │  (REST Layer)   │ │
│  └─────────────┘  └─────────────┘  └────────┬────────┘ │
│                                             │           │
│  ┌──────────────────────────────────────────▼────────┐ │
│  │                  Service Layer                     │ │
│  │  UserService │ StoryService │ ChapterService       │ │
│  │  ReadingService │ SearchService │ NotificationSvc  │ │
│  └──────────────────────────────────────────┬────────┘ │
│                                             │           │
│  ┌──────────────────────────────────────────▼────────┐ │
│  │               Repository Layer (JPA)               │ │
│  └──────────┬───────────────────────────┬────────────┘ │
└─────────────┼───────────────────────────┼──────────────┘
              │                           │
   ┌──────────▼──────────┐    ┌──────────▼──────────┐
   │    PostgreSQL 16     │    │      Redis 7        │
   │  (primary data)      │    │  (cache, sessions,  │
   │                      │    │   rate limit, views)│
   └──────────────────────┘    └─────────────────────┘
                                          │
                               ┌──────────▼──────────┐
                               │       AWS S3         │
                               │  (images, assets)    │
                               └─────────────────────┘
```

### 2.2 Package Structure

```
src/main/java/com/storyverse/
├── config/
│   ├── SecurityConfig.java         # Spring Security + OAuth2 + JWT
│   ├── RedisConfig.java
│   ├── S3Config.java
│   └── OpenApiConfig.java
├── auth/
│   ├── controller/AuthController.java
│   ├── service/AuthService.java
│   ├── dto/                        # TokenResponse, OAuth2UserInfo
│   └── oauth2/
│       ├── CustomOAuth2UserService.java
│       └── OAuth2SuccessHandler.java
├── user/
│   ├── controller/UserController.java
│   ├── service/UserService.java
│   ├── repository/UserRepository.java
│   ├── entity/User.java
│   └── dto/
├── story/
│   ├── controller/StoryController.java
│   ├── service/StoryService.java
│   ├── repository/StoryRepository.java
│   ├── entity/Story.java
│   └── dto/
├── chapter/
│   ├── controller/ChapterController.java
│   ├── service/ChapterService.java
│   ├── repository/ChapterRepository.java
│   ├── entity/Chapter.java
│   └── dto/
├── reading/
│   ├── controller/ReadingController.java
│   ├── service/ReadingService.java
│   ├── repository/
│   │   ├── ReadingProgressRepository.java
│   │   └── LibraryRepository.java
│   └── entity/
│       ├── ReadingProgress.java
│       └── Library.java
├── social/
│   ├── controller/
│   │   ├── CommentController.java
│   │   └── FollowController.java
│   ├── service/
│   ├── repository/
│   └── entity/
│       ├── Comment.java
│       ├── CommentVote.java
│       └── Follow.java
├── search/
│   ├── controller/SearchController.java
│   └── service/SearchService.java
├── notification/
│   ├── service/NotificationService.java
│   ├── repository/NotificationRepository.java
│   └── entity/Notification.java
├── common/
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   └── ForbiddenException.java
│   ├── dto/
│   │   ├── PageResponse.java
│   │   └── ApiResponse.java
│   └── util/
│       ├── SlugUtil.java
│       └── S3Util.java
└── StoryVerseApplication.java
```

---

## 3. Database Schema

### 3.1 Entity Relationship Overview

```
users ──< stories ──< chapters
  │           │
  │           ├──< tags (via story_tags)
  │           ├──< comments ──< comment_votes
  │           └──< reading_list_items
  │
  ├──< follows (follower → following)
  ├──< reading_progress (user × chapter)
  ├──< reading_lists
  └──< notifications
```

### 3.2 Full DDL (Flyway: V1__init.sql)

```sql
-- USERS
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(50) NOT NULL UNIQUE,
    display_name  VARCHAR(100),
    avatar_url    TEXT,
    bio           TEXT,
    provider      VARCHAR(20) NOT NULL DEFAULT 'GOOGLE',
    provider_id   VARCHAR(255) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- STORIES
CREATE TABLE stories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    cover_image_url TEXT,
    language        VARCHAR(10) NOT NULL DEFAULT 'en',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ONGOING | COMPLETED | HIATUS
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC | PRIVATE | UNLISTED
    maturity_rating VARCHAR(20) NOT NULL DEFAULT 'EVERYONE', -- EVERYONE | TEEN | MATURE
    view_count      BIGINT NOT NULL DEFAULT 0,
    word_count      INT NOT NULL DEFAULT 0,
    chapter_count   INT NOT NULL DEFAULT 0,
    search_vector   TSVECTOR,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stories_author ON stories(author_id);
CREATE INDEX idx_stories_slug ON stories(slug);
CREATE INDEX idx_stories_status ON stories(status);
CREATE INDEX idx_stories_search ON stories USING GIN(search_vector);

-- Auto-update search_vector
CREATE OR REPLACE FUNCTION stories_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stories_search_vector_trigger
BEFORE INSERT OR UPDATE ON stories
FOR EACH ROW EXECUTE FUNCTION stories_search_vector_update();

-- CHAPTERS
CREATE TABLE chapters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id        UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    chapter_number  INT NOT NULL,
    word_count      INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | PUBLISHED | SCHEDULED
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(story_id, chapter_number)
);
CREATE INDEX idx_chapters_story ON chapters(story_id);
CREATE INDEX idx_chapters_published ON chapters(story_id, status, chapter_number);

-- TAGS
CREATE TABLE tags (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    slug VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE story_tags (
    story_id UUID REFERENCES stories(id) ON DELETE CASCADE,
    tag_id   INT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (story_id, tag_id)
);

-- COMMENTS
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id  UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES comments(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    vote_count  INT NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comments_chapter ON comments(chapter_id, parent_id, created_at);

CREATE TABLE comment_votes (
    comment_id UUID REFERENCES comments(id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    vote       SMALLINT NOT NULL CHECK(vote IN (-1, 1)),
    PRIMARY KEY (comment_id, user_id)
);

-- FOLLOWS
CREATE TABLE follows (
    follower_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CHECK(follower_id != following_id)
);

-- READING LISTS (Libraries)
CREATE TABLE reading_lists (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reading_lists_user ON reading_lists(user_id);

CREATE TABLE reading_list_items (
    list_id    UUID REFERENCES reading_lists(id) ON DELETE CASCADE,
    story_id   UUID REFERENCES stories(id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (list_id, story_id)
);

-- READING PROGRESS
CREATE TABLE reading_progress (
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    story_id    UUID REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id  UUID REFERENCES chapters(id) ON DELETE SET NULL,
    progress_pct SMALLINT NOT NULL DEFAULT 0 CHECK(progress_pct BETWEEN 0 AND 100),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, story_id)
);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(50) NOT NULL, -- NEW_CHAPTER | NEW_FOLLOWER | COMMENT_REPLY
    payload     JSONB NOT NULL DEFAULT '{}',
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
```

---

## 4. API Design

### 4.1 Base URL & Versioning
```
/api/v1/*
```

### 4.2 Auth Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/auth/oauth2/google` | Redirect to Google OAuth |
| GET | `/oauth2/callback/google` | OAuth2 callback (handled by Spring) |
| POST | `/api/v1/auth/refresh` | Refresh JWT access token |
| POST | `/api/v1/auth/logout` | Invalidate token in Redis |
| GET | `/api/v1/auth/me` | Get current authenticated user |

### 4.3 User Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/users/{username}` | Get public profile |
| PATCH | `/api/v1/users/me` | Update own profile |
| POST | `/api/v1/users/me/avatar` | Upload avatar (multipart) |
| GET | `/api/v1/users/{username}/stories` | Author's published stories |
| POST | `/api/v1/users/{username}/follow` | Follow an author |
| DELETE | `/api/v1/users/{username}/follow` | Unfollow |
| GET | `/api/v1/users/me/feed` | Stories from followed authors |

### 4.4 Story Endpoints

| Method | Path | Description                                     |
|---|---|-------------------------------------------------|
| GET | `/api/v1/stories` | Browse stories (paged, filterable)              |
| POST | `/api/v1/stories` | Create story (only login user can create story) |
| GET | `/api/v1/stories/{slug}` | Get story detail                                |
| PATCH | `/api/v1/stories/{slug}` | Update story metadata                           |
| DELETE | `/api/v1/stories/{slug}` | Delete story                                    |
| POST | `/api/v1/stories/{slug}/cover` | Upload cover image                              |
| GET | `/api/v1/stories/{slug}/tags` | Get story tags                                  |
| PUT | `/api/v1/stories/{slug}/tags` | Replace story tags                              |

### 4.5 Chapter Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/stories/{slug}/chapters` | List chapters |
| POST | `/api/v1/stories/{slug}/chapters` | Create chapter |
| GET | `/api/v1/stories/{slug}/chapters/{number}` | Read chapter |
| PATCH | `/api/v1/stories/{slug}/chapters/{number}` | Update chapter |
| DELETE | `/api/v1/stories/{slug}/chapters/{number}` | Delete chapter |
| POST | `/api/v1/stories/{slug}/chapters/{number}/publish` | Publish chapter |

### 4.6 Social Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/stories/{slug}/chapters/{number}/comments` | Get comments (paged) |
| POST | `/api/v1/stories/{slug}/chapters/{number}/comments` | Post comment |
| PATCH | `/api/v1/comments/{id}` | Edit comment |
| DELETE | `/api/v1/comments/{id}` | Delete comment |
| POST | `/api/v1/comments/{id}/vote` | Vote on comment |

### 4.7 Reading Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/me/library` | Get reading lists |
| POST | `/api/v1/me/library` | Create reading list |
| POST | `/api/v1/me/library/{listId}/stories/{storyId}` | Add to list |
| DELETE | `/api/v1/me/library/{listId}/stories/{storyId}` | Remove from list |
| PUT | `/api/v1/me/reading-progress/{storyId}` | Save reading progress |
| GET | `/api/v1/me/reading-history` | Reading history |

### 4.8 Search Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/search/stories?q=&tag=&status=&sort=` | Search stories |
| GET | `/api/v1/search/users?q=` | Search authors |
| GET | `/api/v1/search/tags?q=` | Autocomplete tags |

---

## 5. Redis Caching Strategy

| Key Pattern | TTL | Purpose |
|---|---|---|
| `jwt:blacklist:{jti}` | Token expiry | Invalidated JWT tokens |
| `story:views:{storyId}` | 5 min | Buffered view counter (flush to DB via scheduled job) |
| `story:hot` | 15 min | Top 20 trending stories (sorted set) |
| `story:{slug}` | 10 min | Story detail cache |
| `user:{username}` | 10 min | User profile cache |
| `feed:{userId}` | 5 min | Personalized feed |
| `ratelimit:{ip}:{endpoint}` | 1 min | Rate limiter token buckets |
| `session:{userId}` | 7 days | Refresh token store |
| `chapter:{chapterId}` | 30 min | Published Delta JSON fetched from S3 |
| `draft:{chapterId}:{userId}` | 24h | Autosave existence flag (real content in S3) |

### View Count Flush (Scheduled Job)
```
Every 5 minutes:
  - GETDEL all story:views:* keys from Redis
  - Batch UPDATE stories SET view_count = view_count + ? WHERE id = ?
```

---

## 6. Auth Flow

```
1. Client hits GET /api/v1/auth/oauth2/google
2. Spring redirects to Google OAuth consent screen
3. User authenticates with Google
4. Google redirects to /oauth2/callback/google?code=...
5. Spring Security exchanges code for Google profile
6. CustomOAuth2UserService:
   a. Check if user exists by email
   b. If not: create user with READER role
   c. If yes: update display_name, avatar from Google (if not customized)
7. OAuth2SuccessHandler:
   a. Generate JWT access token (15 min expiry)
   b. Generate refresh token (7 days), store in Redis
   c. Redirect to frontend with tokens in URL fragment or secure cookie
8. Client stores access token in memory (NOT localStorage)
9. On expiry: POST /api/v1/auth/refresh with refresh token
```

---

## 7. Story Publishing Flow

```
DRAFT → PUBLISHED
  - Validate chapter has content (min 100 words)
  - Set published_at = NOW()
  - Increment stories.chapter_count
  - Update stories.word_count
  - Invalidate story cache in Redis
  - Notify followers (async via Spring @Async)
  - Create notifications for library subscribers
```

---

## 8. Key Spring Boot Configuration

### 8.1 `application.yml` structure
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate.ddl-auto: validate  # Flyway manages schema, not Hibernate
    open-in-view: false           # CRITICAL: always false
  data.redis:
    host: ${REDIS_HOST}
    port: 6379
  security.oauth2.client.registration.google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    scope: email, profile
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  jwt:
    secret: ${JWT_SECRET}      # min 256-bit
    access-token-expiry: 900   # 15 min (seconds)
    refresh-token-expiry: 604800 # 7 days
  aws:
    s3.bucket: ${S3_BUCKET}
    region: ${AWS_REGION}
  pagination:
    default-page-size: 20
    max-page-size: 100
```

### 8.2 Critical Spring Settings
- `open-in-view: false` — prevents lazy loading across HTTP thread, forces explicit fetching
- `ddl-auto: validate` — Flyway owns the schema, Hibernate only validates
- Use `@Transactional(readOnly = true)` on all read-only service methods
- Enable virtual threads: `spring.threads.virtual.enabled: true` (Java 21)

---

## 9. Security Considerations

- JWT stored in memory on client (not localStorage — XSS vulnerable)
- Refresh token stored in HttpOnly Secure cookie
- Rate limiting on auth endpoints: 10 req/min per IP
- Rate limiting on write endpoints: 60 req/min per user
- CORS configured to only allow frontend origin
- All story content sanitized (strip HTML via jsoup before save)
- Mature content gated behind age verification flag on user profile
- User who not login can only read stories, but cannot comment,vote comment, write story, follow, get notification 
- Authors can only modify their own stories/chapters (ownership check in service layer)

---

## 10. Scalability Path

| Phase | Action |
|---|---|
| MVP | Single instance, PostgreSQL full-text search, Redis single node |
| 10K DAU | Add read replicas to PostgreSQL, Redis Sentinel |
| 100K DAU | Add Elasticsearch for search, S3 + CloudFront CDN for images |
| 1M DAU | Microservices split (auth, story, social), Kafka for notifications |
| MVP | S3 for content + metadata in PostgreSQL, Redis autosave draft flag |

---

## 11. Docker Compose (Local Dev)

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: storyverse
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    ports: ["5432:5432"]
    volumes: [postgres_data:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  app:
    build: .
    ports: ["8080:8080"]
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/storyverse
      DB_USER: dev
      DB_PASS: dev
      REDIS_HOST: redis
    depends_on: [postgres, redis]

volumes:
  postgres_data:
```

## 12. Authorization Model

- No role system. Every registered user can read and write.
- Anyone authenticated can create a story
- Only the story's author can add/edit/delete chapters, update metadata, upload cover
- Only the comment's author can edit/delete their own comment
- Any authenticated user can comment, vote, follow, manage their reading list

## 13. Content Storage Strategy

### Format
- Content stored as Quill Delta JSON
- Rendered to HTML on read — never store raw HTML
- Field: chapters.content_url (S3 key), chapters.content_format = 'DELTA'

### S3 Key Structure
- Published content:  content/{chapterId}/published.json
- Version snapshots:  content/{chapterId}/v{versionNumber}.json
- Autosave drafts:    drafts/{chapterId}/{userId}.json

### Read Path
1. Check Redis: chapter:{chapterId}, TTL 30 min
2. [MISS] Fetch S3 object at chapters.content_url
3. Write raw Delta JSON to Redis cache
4. Return to client

### Write Path (manual save)
1. Validate and sanitize content (strip dangerous ops from Delta)
2. Calculate word_count from Delta plain text
3. Upload Delta JSON to S3: content/{chapterId}/published.json
4. Upload snapshot to S3: content/{chapterId}/v{versionNumber}.json
5. UPDATE chapters SET content_url, word_count, updated_at
6. INSERT into chapter_versions
7. UPDATE stories.word_count
8. DELETE Redis: chapter:{chapterId}, story:{slug}

### Autosave Path
1. Client sends Delta every 60 seconds
2. Upload to S3: drafts/{chapterId}/{userId}.json (overwrite)
3. SET Redis: draft:{chapterId}:{userId} = "exists", TTL 24h
   (Redis is existence flag only — actual content lives in S3)
4. Return { savedAt }

### Version Retention
- Keep last 50 draft versions per chapter
- All published snapshots kept forever
- Purge job runs nightly: delete S3 objects + DB rows beyond limit