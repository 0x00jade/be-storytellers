# Application Flows — BE-StoryTellers

Each flow documents the exact sequence of operations Claude must follow.
Steps marked `[ASYNC]` run via `@Async` and must never block the HTTP response.
Steps marked `[CACHE]` touch Redis and must always include TTL.

---

## 1. Auth Flows

### 1.1 Google OAuth Login / Register

```
Client
  │
  ├─→ GET /api/v1/auth/oauth2/google
  │       Spring Security redirects to Google consent screen
  │
  ├─→ User approves on Google
  │
  ├─→ Google redirects to /oauth2/callback/google?code=xxx
  │
  └─→ CustomOAuth2UserService.loadUser()
          │
          ├─ Extract email, name, avatarUrl, providerId from Google profile
          │
          ├─ Lookup user by email in DB
          │
          ├─ [NOT FOUND] Create new user:
          │     - username = slugify(displayName) + random 4-digit suffix
          │     - role = USER
          │     - provider = GOOGLE
          │     - avatarUrl = Google profile picture URL
          │     - INSERT into users
          │
          ├─ [FOUND] Update user:
          │     - Update displayName only if user has never manually changed it
          │     - Update avatarUrl only if user has never uploaded a custom avatar
          │     - UPDATE users SET updated_at = NOW()
          │
          └─→ OAuth2SuccessHandler.onAuthenticationSuccess()
                  │
                  ├─ Generate JWT access token:
                  │     claims: { sub: userId, email, role, jti: UUID }
                  │     expiry: 15 minutes
                  │     signed with HS256 + secret from env
                  │
                  ├─ Generate refresh token:
                  │     value: random UUID
                  │     [CACHE] SET session:{userId} = refreshToken, TTL 7 days
                  │
                  └─ Redirect to {FRONTEND_URL}/auth/callback
                        #access={accessToken}&refresh={refreshToken}
```

---

### 1.2 Token Refresh

```
Client
  │
  └─→ POST /api/v1/auth/refresh { refreshToken }
          │
          ├─ Validate request: refreshToken @NotBlank
          │
          ├─ [CACHE] GET session:{userId} from Redis
          │     [NOT FOUND] → throw UnauthorizedException("Session expired")
          │
          ├─ Compare provided refreshToken == stored value
          │     [MISMATCH] → throw UnauthorizedException("Invalid refresh token")
          │
          ├─ Load user from DB by userId
          │     [NOT FOUND or is_active=false] → throw UnauthorizedException
          │
          ├─ Generate new JWT access token (new jti, same userId)
          │
          ├─ Rotate refresh token:
          │     [CACHE] DELETE session:{userId}
          │     [CACHE] SET session:{userId} = newRefreshToken, TTL 7 days
          │
          └─ Return { accessToken, refreshToken, tokenType, expiresIn }
```

---

### 1.3 Logout

```
Client
  │
  └─→ POST /api/v1/auth/logout (Authorization: Bearer {accessToken})
          │
          ├─ Extract jti and expiry from JWT claims
          │
          ├─ Calculate remaining TTL = token expiry - NOW()
          │
          ├─ [CACHE] SET jwt:blacklist:{jti} = "true", TTL = remaining TTL
          │     (token is now dead even if not expired)
          │
          ├─ Extract userId from JWT claims
          │
          ├─ [CACHE] DELETE session:{userId}
          │
          └─ Return 204 No Content
```

---

### 1.4 Authenticated Request (JWT Filter)

```
Every incoming request
  │
  ├─ Extract Authorization header
  │     [MISSING] → continue as anonymous (Spring decides if endpoint requires auth)
  │
  ├─ Parse JWT: validate signature, check expiry
  │     [INVALID/EXPIRED] → return 401
  │
  ├─ [CACHE] GET jwt:blacklist:{jti}
  │     [FOUND] → return 401 (logged out token)
  │
  ├─ Load UserPrincipal from JWT claims (no DB hit)
  │
  └─ Set SecurityContext → continue to controller
```

---

## 2. User Flows

### 2.1 Update Profile

```
PATCH /api/v1/users/me
  │
  ├─ Validate request fields
  │
  ├─ [USERNAME CHANGE] Check username not already taken
  │     [CONFLICT] → throw ConflictException("Username already taken")
  │
  ├─ Load user from DB by currentUserId
  │
  ├─ Apply changes to entity (only non-null fields from request)
  │
  ├─ UPDATE users
  │
  ├─ [CACHE] DELETE user:{oldUsername}
  │     (invalidate stale profile cache)
  │
  └─ Return updated UserResponse
```

---

### 2.2 Follow / Unfollow Author

```
POST /api/v1/users/{username}/follow
  │
  ├─ Load target user by username
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Check follower != following (cannot follow yourself)
  │     [SAME] → throw BadRequestException
  │
  ├─ Check follow relationship does not already exist
  │     [EXISTS] → throw ConflictException("Already following")
  │
  ├─ INSERT into follows (follower_id, following_id)
  │
  ├─ [CACHE] DELETE feed:{currentUserId}
  │     (feed now includes new author's stories)
  │
  ├─ [ASYNC] Create NEW_FOLLOWER notification for target user
  │
  └─ Return { following: true, followerCount }

DELETE /api/v1/users/{username}/follow
  │
  ├─ Load target user by username
  │
  ├─ DELETE from follows WHERE follower_id = ? AND following_id = ?
  │
  ├─ [CACHE] DELETE feed:{currentUserId}
  │
  └─ Return { following: false, followerCount }
```

---

## 3. Story Flows

### 3.1 Create Story

```
POST /api/v1/stories
  │
  ├─ Validate request
  │
  ├─ Generate slug:
  │     slug = SlugUtil.generate(title, newUUID)
  │     = slugify(title) + "-" + first 8 chars of UUID
  │
  ├─ Check slug uniqueness in DB
  │     [CONFLICT] → append random suffix and retry once
  │     [STILL CONFLICT] → throw ConflictException
  │
  ├─ Validate all tagIds exist in tags table
  │     [ANY MISSING] → throw ValidationException("Tag not found: {id}")
  │
  ├─ INSERT into stories (status=DRAFT, visibility=PUBLIC)
  │
  ├─ INSERT into story_tags for each tagId
  │
  └─ Return 201 StoryDetailResponse
```

---

### 3.2 Update Story

```
PATCH /api/v1/stories/{slug}
  │
  ├─ Load story by slug
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Ownership check:
  │     story.getAuthor().getId() == currentUserId
  │     [FAIL] → throw ForbiddenException
  │
  ├─ [STATUS CHANGE] Validate transition:
  │     DRAFT → ONGOING (allowed, requires at least 1 published chapter)
  │     ONGOING → COMPLETED (allowed)
  │     ONGOING → HIATUS (allowed)
  │     HIATUS → ONGOING (allowed)
  │     COMPLETED → ONGOING (allowed)
  │     * → DRAFT (NEVER allowed once story has published chapters)
  │     [INVALID] → throw ValidationException
  │
  ├─ Apply non-null fields from request
  │
  ├─ UPDATE stories
  │
  ├─ [CACHE] DELETE story:{slug}
  │
  └─ Return updated StoryDetailResponse
```

---

### 3.3 Upload Cover Image

```
POST /api/v1/stories/{slug}/cover
  │
  ├─ Load story, check ownership
  │
  ├─ Validate file:
  │     content-type must be image/jpeg or image/png
  │     size <= 10MB
  │     [FAIL] → throw ValidationException
  │
  ├─ [IF existing cover] Delete old S3 object
  │     key = extract from existing coverImageUrl
  │
  ├─ Generate S3 key: covers/{storyId}/{UUID}.{ext}
  │
  ├─ Upload to S3 with public-read ACL
  │
  ├─ UPDATE stories SET cover_image_url = {s3Url}
  │
  ├─ [CACHE] DELETE story:{slug}
  │
  └─ Return { coverImageUrl }
```

---

## 4. Chapter Flows

### 4.1 Create Chapter

```
POST /api/v1/stories/{slug}/chapters
  │
  ├─ Load story by slug
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Ownership check
  │     [FAIL] → throw ForbiddenException
  │
  ├─ Assign chapterNumber:
  │     SELECT MAX(chapter_number) FROM chapters WHERE story_id = ?
  │     chapterNumber = max + 1 (or 1 if no chapters)
  │
  ├─ INSERT chapters (status=DRAFT, content_url=NULL, word_count=0)
  │
  └─ Return 201 ChapterResponse
```

---

### 4.2 Autosave Chapter Content
```
POST .../autosave
│
├─ Load chapter, ownership check
├─ Sanitize HTML with HtmlUtil.sanitize() (jsoup Safelist.relaxed)
├─ UPDATE chapters SET content = sanitizedHtml, updated_at = NOW()
└─ Return { savedAt }
    (no version record created)
```
### 4.3 Manual Save Chapter Content
```
PUT .../content
│
├─ Load chapter, ownership check
├─ Sanitize HTML with HtmlUtil.sanitize()
├─ Calculate wordCount from HTML plain text with HtmlUtil.countWords()
├─ Get next versionNumber = SELECT MAX(version_number) + 1 FROM chapter_versions
├─ INSERT into chapter_versions (content = sanitizedHtml, wordCount, versionNumber)
├─ UPDATE chapters SET content = sanitizedHtml, word_count = wordCount, updated_at = NOW()
├─ UPDATE stories.word_count if chapter is PUBLISHED (delta = newWordCount - oldWordCount)
└─ Return { versionNumber, wordCount, savedAt }
```

### 4.4 Publish Chapter

```
POST /api/v1/stories/{slug}/chapters/{number}/publish
  │
  ├─ Load story, load chapter by (storyId, chapterNumber)
  │
  ├─ Ownership check
  │
  ├─ Validate chapter:
  │     status must be DRAFT or SCHEDULED
  │     [ALREADY PUBLISHED] → throw ValidationException
  │     wordCount >= 100
  │     [TOO SHORT] → throw ValidationException("Minimum 100 words required")
  │
  ├─ [SCHEDULED] If publishAt is in the future:
  │     UPDATE chapters SET status=SCHEDULED, published_at={publishAt}
  │     Return 200 with status=SCHEDULED
  │     (Scheduler job will pick this up — skip remaining steps)
  │
  ├─ UPDATE chapters SET status=PUBLISHED, published_at=NOW()
  │
  ├─ UPDATE stories SET
  │     chapter_count = chapter_count + 1,
  │     word_count = word_count + chapter.wordCount,
  │     updated_at = NOW()
  │     (also update status to ONGOING if currently DRAFT)
  │
  ├─ [CACHE] DELETE story:{slug}
  │
  ├─ [ASYNC] Notify followers:
  │     SELECT follower_id FROM follows WHERE following_id = authorId
  │     + SELECT user_id FROM reading_list_items WHERE story_id = storyId
  │         (deduplicated)
  │     INSERT into notifications (type=NEW_CHAPTER, payload={...}) for each user
  │
  └─ Return 200 { chapterNumber, status: PUBLISHED, publishedAt }
```

---

### 4.5 Read Chapter

```
GET /api/v1/stories/{slug}/chapters/{number}
  │
  ├─ Load story by slug, load chapter by (storyId, chapterNumber)
  │
  ├─ [IF chapter is DRAFT and currentUser != author] → throw ForbiddenException
  │
  ├─ [CACHE] INCR story:views:{storyId}
  │
  └─ Return ChapterResponse with chapter.content (HTML from DB)
```

---

### 4.4 Delete Chapter

```
DELETE /api/v1/stories/{slug}/chapters/{number}
  │
  ├─ Load story, load chapter
  │
  ├─ Ownership check
  │
  ├─ [IF chapter is PUBLISHED]:
  │     UPDATE stories SET
  │       chapter_count = chapter_count - 1,
  │       word_count = word_count - chapter.wordCount
  │
  ├─ DELETE from chapters
  │     (cascades to comments, comment_votes)
  │
  ├─ [CACHE] DELETE story:{slug}
  │
  └─ Return 204 No Content
```

---

## 5. Comment Flows

### 5.1 Post Comment

```
POST /api/v1/stories/{slug}/chapters/{number}/comments
  │
  ├─ Load chapter, verify it is PUBLISHED
  │     [DRAFT] → throw ForbiddenException
  │
  ├─ [IF parentId provided]:
  │     Load parent comment
  │     [NOT FOUND] → throw ResourceNotFoundException
  │     Verify parent.parentId IS NULL (depth check — no nested replies)
  │     [parent is already a reply] → throw ValidationException("Cannot reply to a reply")
  │
  ├─ INSERT into comments
  │
  ├─ [IF parentId provided — ASYNC]:
  │     Load parent comment author
  │     [author != currentUser] →
  │       INSERT into notifications (type=COMMENT_REPLY) for parent comment author
  │
  └─ Return 201 CommentResponse
```

---

### 5.2 Vote on Comment

```
POST /api/v1/comments/{id}/vote { vote: 1 or -1 }
  │
  ├─ Load comment
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Check existing vote:
  │     SELECT vote FROM comment_votes WHERE comment_id=? AND user_id=?
  │
  ├─ [NO EXISTING VOTE]:
  │     INSERT into comment_votes
  │     UPDATE comments SET vote_count = vote_count + {vote}
  │
  ├─ [SAME VOTE EXISTS] (toggle off):
  │     DELETE from comment_votes
  │     UPDATE comments SET vote_count = vote_count - {existingVote}
  │     Return { voteCount, userVote: null }
  │
  ├─ [DIFFERENT VOTE EXISTS] (flip vote):
  │     UPDATE comment_votes SET vote = {newVote}
  │     UPDATE comments SET vote_count = vote_count + {newVote - existingVote}
  │     (e.g. was -1, now +1: vote_count += 2)
  │
  └─ Return { voteCount, userVote }
```

---

### 5.3 Delete Comment

```
DELETE /api/v1/comments/{id}
  │
  ├─ Load comment
  │
  ├─ Ownership check: comment.getUser().getId() == currentUserId
  │
  ├─ Check if comment has replies:
  │     SELECT COUNT(*) FROM comments WHERE parent_id = ?
  │
  ├─ [HAS REPLIES] Soft delete:
  │     UPDATE comments SET is_deleted=true, content='[deleted]', updated_at=NOW()
  │
  ├─ [NO REPLIES] Hard delete:
  │     DELETE from comment_votes WHERE comment_id = ?
  │     DELETE from comments WHERE id = ?
  │
  └─ Return 204 No Content
```

---

## 6. Reading Flows

### 6.1 Save Reading Progress

```
PUT /api/v1/me/reading-progress/{storyId}
  │
  ├─ Validate storyId exists, chapterId exists and belongs to story
  │
  ├─ Upsert reading_progress:
  │     INSERT INTO reading_progress (user_id, story_id, chapter_id, progress_pct, last_read_at)
  │     VALUES (?, ?, ?, ?, NOW())
  │     ON CONFLICT (user_id, story_id) DO UPDATE
  │       SET chapter_id=excluded.chapter_id,
  │           progress_pct=excluded.progress_pct,
  │           last_read_at=NOW()
  │
  └─ Return { storyId, chapterId, progressPct, lastReadAt }
```

---

### 6.2 Add Story to Reading List

```
POST /api/v1/me/library/{listId}/stories/{storyId}
  │
  ├─ Load reading list by listId
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Check ownership: list.getUserId() == currentUserId
  │     [FAIL] → throw ForbiddenException
  │
  ├─ Load story by storyId
  │     [NOT FOUND] → throw ResourceNotFoundException
  │
  ├─ Check story not already in list:
  │     SELECT 1 FROM reading_list_items WHERE list_id=? AND story_id=?
  │     [EXISTS] → throw ConflictException("Story already in list")
  │
  ├─ INSERT into reading_list_items
  │
  └─ Return { listId, storyId, addedAt }
```

---

## 7. Scheduled Jobs

### 7.1 View Count Flush (every 5 minutes)

```
@Scheduled(fixedDelay = 300_000)
  │
  ├─ KEYS story:views:* from Redis
  │     [NONE] → exit early
  │
  ├─ For each key:
  │     storyId = extract UUID from key
  │     count = GETDEL story:views:{storyId}
  │     (atomic read + delete — prevents double-counting)
  │
  ├─ Batch UPDATE:
  │     UPDATE stories SET view_count = view_count + ? WHERE id = ?
  │     (single DB round-trip for all stories)
  │
  └─ Log: "Flushed view counts for {n} stories"
```

---

### 7.2 Scheduled Chapter Publisher (every 1 minute)

```
@Scheduled(fixedDelay = 60_000)
  │
  ├─ SELECT * FROM chapters
  │     WHERE status = 'SCHEDULED'
  │     AND published_at <= NOW()
  │
  ├─ [NONE] → exit early
  │
  ├─ For each chapter:
  │     UPDATE chapters SET status = 'PUBLISHED'
  │     UPDATE stories SET chapter_count + 1, word_count + chapter.wordCount
  │     [ASYNC] Send NEW_CHAPTER notifications (same as manual publish flow)
  │
  └─ Log: "Auto-published {n} chapters"
```

### 7.3 Version History Purge (nightly at 2AM)
```
@Scheduled(cron = "0 0 2 * * *")
│
├─ Load all draft (is_published = false) chapter_versions
│
├─ Group by chapter_id, sort by version_number DESC
│
├─ For each chapter with more than 50 draft versions:
│     DELETE FROM chapter_versions for excess rows (beyond top 50)
│
└─ Log: "Purged {n} old draft chapter versions"
```
---

## 8. Search Flow

### 8.1 Story Search

```
GET /api/v1/search/stories?q={term}
  │
  ├─ Validate q: length >= 2 chars
  │     [TOO SHORT] → throw ValidationException
  │
  ├─ Build query:
  │     WHERE search_vector @@ plainto_tsquery('english', :q)
  │     AND visibility = 'PUBLIC'
  │     AND status != 'DRAFT'
  │     [+ tag filter if tag param]   AND EXISTS (SELECT 1 FROM story_tags st JOIN tags t ON st.tag_id=t.id WHERE st.story_id=s.id AND t.slug=:tag)
  │     [+ status filter if param]    AND status = :status
  │     [+ lang filter if param]      AND language = :lang
  │
  ├─ Apply sort:
  │     relevance → ORDER BY ts_rank(search_vector, query) DESC
  │     newest    → ORDER BY created_at DESC
  │     popular   → ORDER BY view_count DESC
  │
  ├─ Execute paginated query
  │
  └─ Return PageResponse<StoryCardResponse>
```

---

## Flow Rules for Claude

- **Always check ownership before mutate** — load the entity first, then check author ID
- **Cache invalidation is mandatory** — any flow that writes must delete related cache keys
- **Async steps never block response** — `@Async` on notification and email methods
- **Counters are denormalized** — update `chapter_count`, `word_count`, `vote_count` in the same transaction as the triggering operation, never recalculate via COUNT query at read time
- **Upsert for reading progress** — always use `ON CONFLICT DO UPDATE`, never SELECT then INSERT
- **Soft before hard delete on comments** — check for replies before deciding delete strategy
- **View count never touches DB directly** — always goes through Redis buffer