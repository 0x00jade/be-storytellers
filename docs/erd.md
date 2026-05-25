# ERD — BE-StoryTellers

## Visual Relationship Map

```
users
 ├──< stories (author_id)
 │      ├──< chapters (story_id)
 │      │      └──< comments (chapter_id)
 │      │               ├──< comments (parent_id, self-ref)
 │      │               └──< comment_votes (comment_id)
 │      │
 │      ├──< story_tags (story_id)
 │      │      └──> tags (tag_id)
 │      │
 │      └──< reading_list_items (story_id)
 │
 ├──< reading_lists (user_id)
 │      └──< reading_list_items (list_id)
 │
 ├──< reading_progress (user_id)
 │
 ├──< follows (follower_id)
 ├──< follows (following_id)
 │
 ├──< comment_votes (user_id)
 ├──< comments (user_id)
 └──< notifications (user_id)
```

---

## Tables

### `users`

Central identity table. One record per Google account. Role controls what the user can do.

| Column        | Type          | Constraints                        | Notes                                      |
|---------------|---------------|------------------------------------|--------------------------------------------|
| id            | UUID          | PK, default gen_random_uuid()      |                                            |
| email         | VARCHAR(255)  | NOT NULL, UNIQUE                   | From Google OAuth profile                  |
| username      | VARCHAR(50)   | NOT NULL, UNIQUE                   | Chosen on first login, URL-safe            |
| display_name  | VARCHAR(100)  | NULL                               | Defaults to Google display name            |
| avatar_url    | TEXT          | NULL                               | S3 URL or Google profile picture           |
| bio           | TEXT          | NULL                               | Max 500 chars (enforced in service)        |
| provider      | VARCHAR(20)   | NOT NULL, default 'GOOGLE'         | Enum: GOOGLE                               |
| provider_id   | VARCHAR(255)  | NOT NULL                           | Google sub claim                           |
| is_active     | BOOLEAN       | NOT NULL, default true             | false = soft banned                        |
| created_at    | TIMESTAMPTZ   | NOT NULL, default NOW()            |                                            |
| updated_at    | TIMESTAMPTZ   | NOT NULL, default NOW()            |                                            |

**Indexes**
```sql
CREATE UNIQUE INDEX idx_users_email    ON users(email);
CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE INDEX        idx_users_provider ON users(provider, provider_id);
```

**Business Rules**
- `username` is set once on first login — cannot be changed after 30 days
- `is_active = false` blocks login at JWT generation step, not at DB level
- `provider_id` + `provider` must be unique together (composite unique constraint)

---

### `stories`

One story per author. Contains metadata only — content lives in `chapters`.

| Column          | Type          | Constraints                    | Notes                                            |
|-----------------|---------------|--------------------------------|--------------------------------------------------|
| id              | UUID          | PK, default gen_random_uuid()  |                                                  |
| author_id       | UUID          | NOT NULL, FK → users.id        | CASCADE DELETE                                   |
| title           | VARCHAR(255)  | NOT NULL                       |                                                  |
| slug            | VARCHAR(255)  | NOT NULL, UNIQUE               | slugify(title) + first 8 chars of id             |
| description     | TEXT          | NULL                           | Max 2000 chars                                   |
| cover_image_url | TEXT          | NULL                           | S3 URL                                           |
| language        | VARCHAR(10)   | NOT NULL, default 'en'         | ISO 639-1 code                                   |
| status          | VARCHAR(20)   | NOT NULL, default 'DRAFT'      | Enum: DRAFT, ONGOING, COMPLETED, HIATUS          |
| visibility      | VARCHAR(20)   | NOT NULL, default 'PUBLIC'     | Enum: PUBLIC, PRIVATE, UNLISTED                  |
| maturity_rating | VARCHAR(20)   | NOT NULL, default 'EVERYONE'   | Enum: EVERYONE, TEEN, MATURE                     |
| view_count      | BIGINT        | NOT NULL, default 0            | Buffered via Redis, flushed every 5 min          |
| word_count      | INT           | NOT NULL, default 0            | Sum of all published chapters                    |
| chapter_count   | INT           | NOT NULL, default 0            | Count of PUBLISHED chapters only                 |
| search_vector   | TSVECTOR      | NULL                           | Auto-updated by trigger on title + description   |
| created_at      | TIMESTAMPTZ   | NOT NULL, default NOW()        |                                                  |
| updated_at      | TIMESTAMPTZ   | NOT NULL, default NOW()        |                                                  |

**Indexes**
```sql
CREATE INDEX idx_stories_author        ON stories(author_id);
CREATE UNIQUE INDEX idx_stories_slug   ON stories(slug);
CREATE INDEX idx_stories_status        ON stories(status);
CREATE INDEX idx_stories_visibility    ON stories(visibility, status);
CREATE INDEX idx_stories_search        ON stories USING GIN(search_vector);
CREATE INDEX idx_stories_view_count    ON stories(view_count DESC);
CREATE INDEX idx_stories_created_at    ON stories(created_at DESC);
```

**Trigger — Auto-update search_vector**
```sql
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
```

**Business Rules**
- `status` transitions: DRAFT → ONGOING → COMPLETED or HIATUS. Cannot go back to DRAFT once published
- `visibility = PRIVATE` means only author can read it; excluded from all public listing queries
- `word_count` and `chapter_count` are denormalized counters — updated in service layer on chapter publish/delete
- `slug` is immutable once set — never regenerate on title update (breaks URLs)

---

### `chapters`

Ordered content units of a story. Each chapter has its own publish lifecycle.

| Column         | Type          | Constraints                           | Notes                                         |
|----------------|---------------|---------------------------------------|-----------------------------------------------|
| id             | UUID          | PK, default gen_random_uuid()         |                                               |
| story_id       | UUID          | NOT NULL, FK → stories.id             | CASCADE DELETE                                |
| title          | VARCHAR(255)  | NOT NULL                              |                                               |
| content        | TEXT          | NULL                                  | HTML string; NULL until first save            |
| chapter_number | INT           | NOT NULL                              | 1-based, unique per story                     |
| word_count     | INT           | NOT NULL, default 0                   | Calculated from HTML plain text on save       |
| status         | VARCHAR(20)   | NOT NULL, default 'DRAFT'             | Enum: DRAFT, PUBLISHED, SCHEDULED             |
| published_at   | TIMESTAMPTZ   | NULL                                  | Set when status → PUBLISHED                   |
| created_at     | TIMESTAMPTZ   | NOT NULL, default NOW()               |                                               |
| updated_at     | TIMESTAMPTZ   | NOT NULL, default NOW()               |                                               |

**Constraints**
```sql
UNIQUE(story_id, chapter_number)
```

**Indexes**
```sql
CREATE INDEX idx_chapters_story     ON chapters(story_id);
CREATE INDEX idx_chapters_published ON chapters(story_id, status, chapter_number);
CREATE INDEX idx_chapters_status    ON chapters(status, published_at);
```

**Business Rules**
- `chapter_number` is assigned by service as `MAX(chapter_number) + 1` per story — never by client
- Min 100 words required to publish (enforced in service)
- Deleting a published chapter decrements `stories.chapter_count` and `stories.word_count`
- `content` is sanitized with jsoup (`Safelist.relaxed()`) before every save
- `word_count` derived from HTML plain text: `Jsoup.parse(html).text()`, count whitespace-delimited tokens
- SCHEDULED chapters are published by a `@Scheduled` job that checks `published_at <= NOW()`
- Autosave updates `content` column only — no version record created
- Manual save (`PUT /content`) updates `content` column AND creates a `chapter_versions` record
---

### `chapter_versions`

Immutable version history. One row per save or publish event.

| Column         | Type        | Constraints                              | Notes                                |
|----------------|-------------|------------------------------------------|--------------------------------------|
| id             | UUID        | PK, default gen_random_uuid()            |                                      |
| chapter_id     | UUID        | NOT NULL, FK → chapters.id CASCADE       |                                      |
| version_number | INT         | NOT NULL                                 | 1-based, increments per chapter      |
| content        | TEXT        | NULL                                     | HTML snapshot at time of save        |
| word_count     | INT         | NOT NULL                                 |                                      |
| is_published   | BOOLEAN     | NOT NULL, default false                  | true = this version went live        |
| saved_by       | UUID        | FK → users.id SET NULL                   |                                      |
| created_at     | TIMESTAMPTZ | NOT NULL, default NOW()                  |                                      |


**Constraints**
```sql
UNIQUE(chapter_id, version_number)
```

**Indexes**
```sql
CREATE INDEX idx_chapter_versions_chapter ON chapter_versions(chapter_id, version_number DESC);
```

### `tags`

Global tag registry. Authors assign tags to stories, readers filter by tags.

| Column | Type         | Constraints           | Notes                        |
|--------|--------------|-----------------------|------------------------------|
| id     | SERIAL       | PK                    | Integer, auto-increment      |
| name   | VARCHAR(50)  | NOT NULL, UNIQUE      | Display label e.g. "Fantasy" |
| slug   | VARCHAR(50)  | NOT NULL, UNIQUE      | URL-safe e.g. "fantasy"      |

**Indexes**
```sql
CREATE UNIQUE INDEX idx_tags_name ON tags(name);
CREATE UNIQUE INDEX idx_tags_slug ON tags(slug);
```

**Business Rules**
- Tags can be created by author if not existed, 
- Max 10 tags per story (enforced in service)
- Tag names are always lowercase on creation

---

### `story_tags`

Join table. Links stories to their tags. No extra columns.

| Column   | Type | Constraints                      |
|----------|------|----------------------------------|
| story_id | UUID | PK, FK → stories.id CASCADE      |
| tag_id   | INT  | PK, FK → tags.id CASCADE         |

```sql
PRIMARY KEY (story_id, tag_id)
```

---

### `comments`

Threaded comments on chapters. Supports one level of nesting (reply to top-level comment only).

| Column     | Type          | Constraints                        | Notes                                          |
|------------|---------------|------------------------------------|------------------------------------------------|
| id         | UUID          | PK, default gen_random_uuid()      |                                                |
| chapter_id | UUID          | NOT NULL, FK → chapters.id         | CASCADE DELETE                                 |
| user_id    | UUID          | NOT NULL, FK → users.id            | CASCADE DELETE                                 |
| parent_id  | UUID          | NULL, FK → comments.id             | CASCADE DELETE. NULL = top-level comment       |
| content    | TEXT          | NOT NULL                           | Max 2000 chars. Plain text only, no HTML       |
| vote_count | INT           | NOT NULL, default 0                | Denormalized. Updated on comment_votes insert  |
| is_deleted | BOOLEAN       | NOT NULL, default false            | Soft delete — content replaced with [deleted]  |
| created_at | TIMESTAMPTZ   | NOT NULL, default NOW()            |                                                |
| updated_at | TIMESTAMPTZ   | NOT NULL, default NOW()            |                                                |

**Indexes**
```sql
CREATE INDEX idx_comments_chapter    ON comments(chapter_id, parent_id, created_at);
CREATE INDEX idx_comments_user       ON comments(user_id);
CREATE INDEX idx_comments_parent     ON comments(parent_id);
```

**Business Rules**
- Max nesting depth = 1. Replies cannot have `parent_id` that itself has a `parent_id`
- Soft delete: set `is_deleted = true`, replace content with `[deleted]` — never hard delete if it has replies
- Hard delete allowed only if comment has no replies
- `vote_count` updated atomically: `UPDATE comments SET vote_count = vote_count + 1 WHERE id = ?`

---

### `comment_votes`

One vote per user per comment. Vote is either +1 (upvote) or -1 (downvote).

| Column     | Type      | Constraints                          |
|------------|-----------|--------------------------------------|
| comment_id | UUID      | PK, FK → comments.id CASCADE         |
| user_id    | UUID      | PK, FK → users.id CASCADE            |
| vote       | SMALLINT  | NOT NULL, CHECK (vote IN (-1, 1))    |

```sql
PRIMARY KEY (comment_id, user_id)
```

**Business Rules**
- Voting again with same value = remove vote (toggle off)
- Voting with opposite value = change vote
- On insert/update/delete: update `comments.vote_count` accordingly

---

### `follows`

User follows author. Both sides are `users.id`. Self-follow prevented by CHECK constraint.

| Column       | Type        | Constraints                      |
|--------------|-------------|----------------------------------|
| follower_id  | UUID        | PK, FK → users.id CASCADE        |
| following_id | UUID        | PK, FK → users.id CASCADE        |
| created_at   | TIMESTAMPTZ | NOT NULL, default NOW()          |

```sql
PRIMARY KEY (follower_id, following_id)
CHECK (follower_id != following_id)
```

**Indexes**
```sql
CREATE INDEX idx_follows_follower  ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);
```

**Business Rules**
- Following a user you already follow is a no-op (upsert pattern)
- Feed query: `SELECT * FROM stories WHERE author_id IN (SELECT following_id FROM follows WHERE follower_id = ?) AND visibility = 'PUBLIC' AND status != 'DRAFT'`

---

### `reading_lists`

Named collections of stories per user. Each user gets a default "Library" list on signup.

| Column     | Type          | Constraints                      | Notes                              |
|------------|---------------|----------------------------------|------------------------------------|
| id         | UUID          | PK, default gen_random_uuid()    |                                    |
| user_id    | UUID          | NOT NULL, FK → users.id CASCADE  |                                    |
| name       | VARCHAR(100)  | NOT NULL                         | e.g. "Reading", "Favorites"        |
| is_default | BOOLEAN       | NOT NULL, default false          | One default list per user          |
| created_at | TIMESTAMPTZ   | NOT NULL, default NOW()          |                                    |

**Indexes**
```sql
CREATE INDEX idx_reading_lists_user ON reading_lists(user_id);
```

**Constraints**
```sql
-- Only one default list per user (partial unique index)
CREATE UNIQUE INDEX idx_reading_lists_default ON reading_lists(user_id) WHERE is_default = true;
```

**Business Rules**
- Default list created automatically in `UserService.createUser()`
- Default list cannot be deleted or renamed below 1 list per user
- Max 20 reading lists per user (enforced in service)

---

### `reading_list_items`

Stories saved to a reading list.

| Column   | Type        | Constraints                           |
|----------|-------------|---------------------------------------|
| list_id  | UUID        | PK, FK → reading_lists.id CASCADE     |
| story_id | UUID        | PK, FK → stories.id CASCADE           |
| added_at | TIMESTAMPTZ | NOT NULL, default NOW()               |

```sql
PRIMARY KEY (list_id, story_id)
```

---

### `reading_progress`

Tracks which chapter a user last read per story and scroll position percentage.

| Column       | Type        | Constraints                              | Notes                                  |
|--------------|-------------|------------------------------------------|----------------------------------------|
| user_id      | UUID        | PK, FK → users.id CASCADE               |                                        |
| story_id     | UUID        | PK, FK → stories.id CASCADE             |                                        |
| chapter_id   | UUID        | NULL, FK → chapters.id SET NULL          | Last chapter read                      |
| progress_pct | SMALLINT    | NOT NULL, default 0                      | 0–100 scroll % within current chapter |
| last_read_at | TIMESTAMPTZ | NOT NULL, default NOW()                  |                                        |

```sql
PRIMARY KEY (user_id, story_id)
CHECK (progress_pct BETWEEN 0 AND 100)
```

**Indexes**
```sql
CREATE INDEX idx_reading_progress_user  ON reading_progress(user_id, last_read_at DESC);
CREATE INDEX idx_reading_progress_story ON reading_progress(story_id);
```

**Business Rules**
- Upsert on every chapter read: `INSERT ... ON CONFLICT (user_id, story_id) DO UPDATE`
- `progress_pct = 100` does not mean story is finished — only that the current chapter is done
- Reading history query orders by `last_read_at DESC`

---

### `notifications`

In-app notifications. Payload is flexible JSONB to support different notification types.

| Column     | Type        | Constraints                      | Notes                                     |
|------------|-------------|----------------------------------|-------------------------------------------|
| id         | UUID        | PK, default gen_random_uuid()    |                                           |
| user_id    | UUID        | NOT NULL, FK → users.id CASCADE  | Recipient                                 |
| type       | VARCHAR(50) | NOT NULL                         | Enum: NEW_CHAPTER, NEW_FOLLOWER, COMMENT_REPLY, STORY_COMPLETE |
| payload    | JSONB       | NOT NULL, default '{}'           | Type-specific data (see below)            |
| is_read    | BOOLEAN     | NOT NULL, default false          |                                           |
| created_at | TIMESTAMPTZ | NOT NULL, default NOW()          |                                           |

**Indexes**
```sql
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
```

**Payload shapes by type**

```json
// NEW_CHAPTER
{
  "storyId": "uuid",
  "storyTitle": "My Story",
  "storySlug": "my-story-a1b2c3d4",
  "chapterNumber": 5,
  "chapterTitle": "The Twist",
  "authorUsername": "john"
}

// NEW_FOLLOWER
{
  "followerUserId": "uuid",
  "followerUsername": "jane",
  "followerAvatarUrl": "https://..."
}

// COMMENT_REPLY
{
  "commentId": "uuid",
  "storySlug": "my-story-a1b2c3d4",
  "chapterNumber": 3,
  "replierUsername": "alice",
  "preview": "First 100 chars of reply..."
}
```

**Business Rules**
- Notifications created asynchronously via `@Async` — never block the main request
- Max 200 unread notifications per user — oldest are soft-purged when limit reached
- Batch mark-as-read: `UPDATE notifications SET is_read = true WHERE user_id = ? AND is_read = false`

---

## Relationship Summary

| Relationship                          | Type       | FK                                      | On Delete     |
|---------------------------------------|------------|-----------------------------------------|---------------|
| users → stories                       | 1 to many  | stories.author_id → users.id            | CASCADE       |
| stories → chapters                    | 1 to many  | chapters.story_id → stories.id          | CASCADE       |
| chapters → comments                   | 1 to many  | comments.chapter_id → chapters.id       | CASCADE       |
| users → comments                      | 1 to many  | comments.user_id → users.id             | CASCADE       |
| comments → comments (replies)         | 1 to many  | comments.parent_id → comments.id        | CASCADE       |
| comments → comment_votes              | 1 to many  | comment_votes.comment_id → comments.id  | CASCADE       |
| users → comment_votes                 | 1 to many  | comment_votes.user_id → users.id        | CASCADE       |
| stories ↔ tags                        | many-many  | via story_tags                          | CASCADE both  |
| users → follows (as follower)         | 1 to many  | follows.follower_id → users.id          | CASCADE       |
| users → follows (as following)        | 1 to many  | follows.following_id → users.id         | CASCADE       |
| users → reading_lists                 | 1 to many  | reading_lists.user_id → users.id        | CASCADE       |
| reading_lists ↔ stories               | many-many  | via reading_list_items                  | CASCADE both  |
| users + stories → reading_progress    | 1 to 1     | composite PK (user_id, story_id)        | CASCADE       |
| reading_progress → chapters           | many to 1  | reading_progress.chapter_id → chapters  | SET NULL      |
| users → notifications                 | 1 to many  | notifications.user_id → users.id        | CASCADE       |

---

## Enum Reference

### `stories.status`
| Value     | Description                                          |
|-----------|------------------------------------------------------|
| DRAFT     | Not visible publicly. Author editing                 |
| ONGOING   | Actively being updated with new chapters             |
| COMPLETED | Story is finished, no more chapters expected         |
| HIATUS    | Temporarily paused                                   |

### `stories.visibility`
| Value    | Description                                          |
|----------|------------------------------------------------------|
| PUBLIC   | Visible to all users and search                      |
| UNLISTED | Accessible via direct link only, excluded from search|
| PRIVATE  | Author only                                          |

### `stories.maturity_rating`
| Value    | Description                                          |
|----------|------------------------------------------------------|
| EVERYONE | Safe for all ages                                    |
| TEEN     | 13+, mild themes                                     |
| MATURE   | 17+, requires age flag on user profile               |

### `chapters.status`
| Value     | Description                                          |
|-----------|------------------------------------------------------|
| DRAFT     | Not visible to readers                               |
| PUBLISHED | Live and readable                                    |
| SCHEDULED | Will auto-publish at `published_at` timestamp        |

### `notifications.type`
| Value           | Triggered when                                  |
|-----------------|-------------------------------------------------|
| NEW_CHAPTER     | Author publishes a chapter on a story user follows or has in library |
| NEW_FOLLOWER    | Someone follows the user                        |
| COMMENT_REPLY   | Someone replies to user's comment               |
| STORY_COMPLETE  | Story user follows is marked COMPLETED          |

---

## Java Entity → DB Column Mapping

| Java Type      | DB Type     | Notes                                     |
|----------------|-------------|-------------------------------------------|
| UUID           | UUID        | `@GeneratedValue(strategy = UUID)`        |
| String         | VARCHAR/TEXT| Use `@Column(length = n)` to match DDL    |
| LocalDateTime  | TIMESTAMPTZ | Use `@Column(columnDefinition = "TIMESTAMPTZ")` |
| Boolean        | BOOLEAN     |                                           |
| Integer        | INT         |                                           |
| Long           | BIGINT      | For view counts                           |
| Enum           | VARCHAR(20) | `@Enumerated(EnumType.STRING)` always     |
| JSONB          | JSONB       | Map as `String` with `@Column(columnDefinition = "jsonb")` |

**Critical**: Never use `@Enumerated(EnumType.ORDINAL)` — ordinal breaks on enum reorder.