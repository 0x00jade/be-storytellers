# API Contracts — BE-StoryTellers

## Conventions

- Base URL: `/api/v1`
- All requests: `Content-Type: application/json`
- All responses wrapped in `ApiResponse<T>`:
  ```json
  { "success": true, "data": { ... }, "message": null }
  { "success": false, "data": null, "message": "Human readable error" }
  ```
- Pagination always uses `PageResponse<T>`:
  ```json
  {
    "content": [...],
    "page": 0,
    "size": 20,
    "total": 150,
    "totalPages": 8
  }
  ```
- Timestamps: ISO 8601 UTC — `2025-01-15T10:30:00Z`
- IDs: UUID string — `"3fa85f64-5717-4562-b3fc-2c963f66afa6"`
- Auth: `Authorization: Bearer {accessToken}` header on all protected endpoints
- 🔒 = requires authentication (ownership verified at runtime)
- 🛡️ = requires ADMIN role

---

## Error Reference

| HTTP | Code                | When                                          |
|------|---------------------|-----------------------------------------------|
| 400  | VALIDATION_ERROR    | Bean validation fails on request body         |
| 400  | BAD_REQUEST         | Business rule violation                       |
| 401  | UNAUTHORIZED        | Missing, expired, or blacklisted token        |
| 403  | FORBIDDEN           | Valid token but wrong owner or role           |
| 404  | STORY_NOT_FOUND     | Story slug does not exist                     |
| 404  | USER_NOT_FOUND      | Username does not exist                       |
| 404  | CHAPTER_NOT_FOUND   | Chapter number does not exist for story       |
| 404  | COMMENT_NOT_FOUND   | Comment ID does not exist                     |
| 404  | LIST_NOT_FOUND      | Reading list ID does not exist                |
| 409  | SLUG_CONFLICT       | Title generates a duplicate slug              |
| 409  | ALREADY_FOLLOWING   | User already follows this author              |
| 409  | ALREADY_IN_LIST     | Story already in reading list                 |
| 429  | RATE_LIMIT_EXCEEDED | Too many requests                             |
| 500  | INTERNAL_ERROR      | Unexpected server error                       |

---

## 1. Auth

### GET `/auth/oauth2/google`
Redirect to Google OAuth consent screen. No request body. Handled by Spring Security.

---

### POST `/auth/verify-token`
Exchange a Google OAuth **access token** for app-level JWT tokens. Use this after a successful Google Sign-In on the frontend to get tokens for all subsequent API calls.

**Request**
```json
{ "accessToken": "ya29.a0AfB_..." }
```

> The `accessToken` field must contain the Google **access token** (the `ya29.xxx` token returned by Google Sign-In, not the ID token).

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "message": null
}
```

| Field        | Description                                                         |
|--------------|---------------------------------------------------------------------|
| accessToken  | Short-lived JWT. Include in `Authorization: Bearer {accessToken}` header |
| refreshToken | Long-lived UUID. Store securely; use to get a new access token      |
| tokenType    | Always `"Bearer"`                                                   |
| expiresIn    | Access token lifetime in seconds                                    |

**Errors**
| Status | Code         | When                                         |
|--------|--------------|----------------------------------------------|
| 400    | BAD_REQUEST  | `accessToken` field is missing or blank      |
| 401    | UNAUTHORIZED | Google rejected the token (invalid/expired)  |
| 401    | UNAUTHORIZED | Google account email is not verified         |
| 401    | UNAUTHORIZED | Account has been deactivated                 |

**Frontend flow**
```js
// 1. Sign in with Google (e.g. Google Identity Services)
const { access_token } = await googleSignIn();

// 2. Exchange for app tokens
const res = await fetch('/api/v1/auth/verify-token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ accessToken: access_token })
});
const { data } = await res.json();
// store data.accessToken and data.refreshToken
```

---

### POST `/auth/refresh`
Exchange a refresh token for a new access token.

**Request**
```json
{ "refreshToken": "550e8400-e29b-41d4-a716-446655440000" }
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "661f9511-f30c-52e5-b827-557766551111",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "message": null
}
```

**Errors**
| Status | Code         | When                              |
|--------|--------------|-----------------------------------|
| 401    | UNAUTHORIZED | Refresh token not found in Redis  |
| 401    | UNAUTHORIZED | Refresh token expired             |

---

### POST `/auth/logout` 🔒
Invalidate current access token and refresh token.

**Request** — no body

**Response 204** — no body

---

### GET `/auth/me` 🔒
Get the currently authenticated user.

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@gmail.com",
    "username": "johndoe",
    "displayName": "John Doe",
    "avatarUrl": "https://storytellers-content.s3.us-east-1.amazonaws.com/avatars/...",
    "bio": "I write fantasy stories",
    "role": "AUTHOR",
    "createdAt": "2025-01-01T00:00:00Z"
  },
  "message": null
}
```

---

## 2. Users

### GET `/users/{username}`
Get a public user profile.

**Path Params**
| Param    | Type   | Required | Notes              |
|----------|--------|----------|--------------------|
| username | String | Yes      | URL-safe username  |

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "username": "johndoe",
    "displayName": "John Doe",
    "avatarUrl": "https://storytellers-content.s3.us-east-1.amazonaws.com/avatars/...",
    "bio": "I write fantasy stories",
    "role": "AUTHOR",
    "followerCount": 120,
    "followingCount": 45,
    "storyCount": 8,
    "isFollowing": false,
    "createdAt": "2025-01-01T00:00:00Z"
  },
  "message": null
}
```

**Errors**
| Status | Code           | When                    |
|--------|----------------|-------------------------|
| 404    | USER_NOT_FOUND | Username does not exist |

---

### PATCH `/users/me` 🔒
Update own profile.

**Request** — all fields optional
```json
{
  "displayName": "John Doe",
  "bio": "I write fantasy and sci-fi",
  "username": "johndoe2"
}
```

**Validation**
| Field       | Rules                                      |
|-------------|--------------------------------------------|
| displayName | `@Size(max = 100)`                         |
| bio         | `@Size(max = 500)`                         |
| username    | `@Size(min = 3, max = 50)`, alphanumeric + underscore only |

**Response 200** — same shape as `GET /users/{username}`

**Errors**
| Status | Code             | When                        |
|--------|------------------|-----------------------------|
| 400    | VALIDATION_ERROR | Field fails validation      |
| 409    | SLUG_CONFLICT    | Username already taken      |

---

### POST `/users/me/avatar` 🔒
Upload profile avatar. Multipart form data.

**Request** — `multipart/form-data`
| Field | Type | Required | Notes                          |
|-------|------|----------|--------------------------------|
| file  | File | Yes      | JPEG or PNG, max 5MB           |

**Response 200**
```json
{
  "success": true,
  "data": { "avatarUrl": "https://storytellers-content.s3.us-east-1.amazonaws.com/avatars/{userId}/{uuid}.jpg" },
  "message": null
}
```

**Errors**
| Status | Code             | When                         |
|--------|------------------|------------------------------|
| 400    | BAD_REQUEST      | File type not JPEG/PNG       |
| 400    | BAD_REQUEST      | File exceeds 5MB             |
| 401    | UNAUTHORIZED     | Not authenticated            |

**Frontend example**
```js
const form = new FormData();
form.append('file', fileInput.files[0]); // JPEG or PNG, max 5MB

const res = await fetch('/api/v1/users/me/avatar', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + accessToken },
  body: form  // Do NOT set Content-Type manually — browser sets multipart boundary
});
```

---

### GET `/users/{username}/stories`
List published stories by an author. Paginated.

**Query Params**
| Param  | Type    | Default | Notes                            |
|--------|---------|---------|----------------------------------|
| page   | Integer | 0       |                                  |
| size   | Integer | 20      | Max 100                          |
| status | String  | null    | Filter: ONGOING, COMPLETED, HIATUS |

**Response 200** — `PageResponse<StoryCardResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "slug": "my-story-a1b2c3d4",
        "title": "My Story",
        "description": "A tale of...",
        "coverImageUrl": "https://storytellers-content.s3.us-east-1.amazonaws.com/covers/...",
        "author": { "id": "uuid", "username": "johndoe", "displayName": "John Doe", "avatarUrl": "..." },
        "status": "ONGOING",
        "maturityRating": "EVERYONE",
        "language": "en",
        "viewCount": 1500,
        "chapterCount": 12,
        "wordCount": 48000,
        "tags": [{ "id": 1, "name": "Fantasy", "slug": "fantasy" }],
        "updatedAt": "2025-01-15T10:30:00Z",
        "createdAt": "2025-01-01T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 8,
    "totalPages": 1
  },
  "message": null
}
```

---

### POST `/users/{username}/follow` 🔒
Follow an author.

**Request** — no body

**Response 200**
```json
{ "success": true, "data": { "following": true, "followerCount": 121 }, "message": null }
```

**Errors**
| Status | Code             | When                         |
|--------|------------------|------------------------------|
| 404    | USER_NOT_FOUND   | Username does not exist      |
| 409    | ALREADY_FOLLOWING| Already following this user  |

---

### DELETE `/users/{username}/follow` 🔒
Unfollow an author.

**Response 200**
```json
{ "success": true, "data": { "following": false, "followerCount": 120 }, "message": null }
```

---

### GET `/users/me/feed` 🔒
Paginated stories from followed authors, newest chapters first.

**Query Params**
| Param | Type    | Default |
|-------|---------|---------|
| page  | Integer | 0       |
| size  | Integer | 20      |

**Response 200** — `PageResponse<StoryCardResponse>` (same shape as above)

---

## 3. Stories

### GET `/stories`
Browse all public stories. Paginated and filterable.

**Query Params**
| Param   | Type    | Default    | Notes                                        |
|---------|---------|------------|----------------------------------------------|
| page    | Integer | 0          |                                              |
| size    | Integer | 20         | Max 100                                      |
| tag     | String  | null       | Tag slug e.g. `fantasy`                      |
| status  | String  | null       | ONGOING, COMPLETED, HIATUS                   |
| lang    | String  | null       | ISO 639-1 e.g. `en`                          |
| sort    | String  | `newest`   | `newest`, `popular`, `updated`               |
| mature  | Boolean | false      | Include MATURE rated stories                 |

**Response 200** — `PageResponse<StoryCardResponse>` (same shape as user stories above)

---

### POST `/stories` 🔒
Create a new story. Requires AUTHOR role.

**Request**
```json
{
  "title": "The Last Kingdom",
  "description": "In a world where magic is outlawed...",
  "language": "en",
  "maturityRating": "EVERYONE",
  "tagIds": [1, 3]
}
```

**Validation**
| Field         | Rules                                         |
|---------------|-----------------------------------------------|
| title         | `@NotBlank`, `@Size(min = 3, max = 255)`      |
| description   | `@Size(max = 2000)`                           |
| language      | `@NotBlank`, valid ISO 639-1 code             |
| maturityRating| `@NotNull`, one of: EVERYONE, TEEN, MATURE    |
| tagIds        | Max 10 tag IDs, each must exist               |

**Response 201**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "slug": "the-last-kingdom-a1b2c3d4",
    "title": "The Last Kingdom",
    "description": "In a world where magic is outlawed...",
    "coverImageUrl": null,
    "author": { "id": "uuid", "username": "johndoe", "displayName": "John Doe", "avatarUrl": "..." },
    "language": "en",
    "status": "DRAFT",
    "visibility": "PUBLIC",
    "maturityRating": "EVERYONE",
    "viewCount": 0,
    "chapterCount": 0,
    "wordCount": 0,
    "tags": [{ "id": 1, "name": "Fantasy", "slug": "fantasy" }],
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  },
  "message": null
}
```

**Errors**
| Status | Code             | When                          |
|--------|------------------|-------------------------------|
| 400    | VALIDATION_ERROR | Field fails validation        |
| 403    | FORBIDDEN        | User does not have AUTHOR role|
| 409    | SLUG_CONFLICT    | Generated slug already exists |

---

### GET `/stories/{slug}`
Get story detail by slug.

**Response 200** — full `StoryDetailResponse` (same as POST response above)

**Errors**
| Status | Code            | When                      |
|--------|-----------------|---------------------------|
| 404    | STORY_NOT_FOUND | Slug does not exist       |
| 403    | FORBIDDEN       | Story is PRIVATE          |

---

### PATCH `/stories/{slug}` 🔒
Update story metadata. Author only. All fields optional.

**Request**
```json
{
  "title": "The Last Kingdom: Revised",
  "description": "Updated description...",
  "status": "ONGOING",
  "visibility": "PUBLIC",
  "maturityRating": "TEEN",
  "language": "en"
}
```

**Validation** — same rules as POST, all optional

**Response 200** — full `StoryDetailResponse`

**Errors**
| Status | Code             | When                              |
|--------|------------------|-----------------------------------|
| 403    | FORBIDDEN        | Not the story author              |
| 404    | STORY_NOT_FOUND  | Slug does not exist               |
| 400    | BAD_REQUEST      | Invalid status transition         |

---

### DELETE `/stories/{slug}` 🔒
Delete a story and all its chapters. Author only.

**Response 204** — no body

**Errors**
| Status | Code            | When                  |
|--------|-----------------|-----------------------|
| 403    | FORBIDDEN       | Not the story author  |
| 404    | STORY_NOT_FOUND | Slug does not exist   |

---

### POST `/stories/{slug}/cover` 🔒
Upload story cover image. Author only. Multipart form data. Replaces any existing cover.

**Request** — `multipart/form-data`
| Field | Type | Required | Notes                 |
|-------|------|----------|-----------------------|
| file  | File | Yes      | JPEG or PNG, max 10MB |

**Response 200**
```json
{
  "success": true,
  "data": { "coverImageUrl": "https://storytellers-content.s3.us-east-1.amazonaws.com/covers/{storyId}/{uuid}.jpg" },
  "message": null
}
```

**Errors**
| Status | Code            | When                          |
|--------|-----------------|-------------------------------|
| 400    | BAD_REQUEST     | File type not JPEG/PNG        |
| 400    | BAD_REQUEST     | File exceeds 10MB             |
| 403    | FORBIDDEN       | Not the story author          |
| 404    | STORY_NOT_FOUND | Story slug does not exist     |

**Frontend example**
```js
const form = new FormData();
form.append('file', fileInput.files[0]); // JPEG or PNG, max 10MB

const res = await fetch(`/api/v1/stories/${slug}/cover`, {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + accessToken },
  body: form
});
```

---

### PUT `/stories/{slug}/tags` 🔒
Replace all tags on a story. Author only.

**Request**
```json
{ "tagIds": [1, 2, 5] }
```

**Validation**
| Field  | Rules              |
|--------|--------------------|
| tagIds | Max 10, all must exist |

**Response 200**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Fantasy", "slug": "fantasy" },
    { "id": 2, "name": "Adventure", "slug": "adventure" }
  ],
  "message": null
}
```

---

## 4. Chapters

### GET `/stories/{slug}/chapters`
List all chapters for a story. Ordered by `chapterNumber` ASC.

**Query Params**
| Param  | Type    | Default | Notes                              |
|--------|---------|---------|------------------------------------|
| page   | Integer | 0       |                                    |
| size   | Integer | 20      |                                    |
| status | String  | PUBLISHED | PUBLISHED only (readers), all statuses for author |

**Response 200** — `PageResponse<ChapterSummaryResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "chapterNumber": 1,
        "title": "The Beginning",
        "wordCount": 3200,
        "status": "PUBLISHED",
        "publishedAt": "2025-01-10T08:00:00Z"
      }
    ],
    "page": 0, "size": 20, "total": 12, "totalPages": 1
  },
  "message": null
}
```

---

### POST `/stories/{slug}/chapters` 🔒
Create a new chapter in DRAFT status.

**Request**
```json
{
  "title": "The Beginning"
}
```

**Validation**
| Field   | Rules                                     |
|---------|-------------------------------------------|
| title   | `@NotBlank`, `@Size(min = 1, max = 255)`  |
| content | `@NotBlank`                               |

**Response 201**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "storySlug": "the-last-kingdom-a1b2c3d4",
    "chapterNumber": 13,
    "title": "The Beginning",
    "content": "<p>Once upon a time...</p>",
    "wordCount": 650,
    "status": "DRAFT",
    "publishedAt": null,
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  },
  "message": null
}
```

**Errors**
| Status | Code            | When                              |
|--------|-----------------|-----------------------------------|
| 403    | FORBIDDEN       | Not the story author              |
| 404    | STORY_NOT_FOUND | Slug does not exist               |

---

### GET `/stories/{slug}/chapters/{number}`
Read a chapter by its number. Increments view count via Redis.

**Path Params**
| Param  | Type    | Notes        |
|--------|---------|--------------|
| slug   | String  | Story slug   |
| number | Integer | Chapter number (1-based) |

**Response 200** — full `ChapterResponse` (same shape as POST response)

**Errors**
| Status | Code               | When                            |
|--------|--------------------|---------------------------------|
| 404    | STORY_NOT_FOUND    | Story slug does not exist       |
| 404    | CHAPTER_NOT_FOUND  | Chapter number does not exist   |
| 403    | FORBIDDEN          | Chapter is DRAFT and not author |

---

### PATCH `/stories/{slug}/chapters/{number}` 🔒
Update chapter content or title. Author only.

**Request** — all fields optional
```json
{
  "title": "A New Beginning"
}
```

**Response 200** — full `ChapterResponse`

---

### POST `/stories/{slug}/chapters/{number}/autosave` 🔒
Save draft HTML content to the DB without creating a version record. Author only.

**Request**
```json
{ "content": "<p>Once upon a time...</p>" }
```

**Response 200**
```json
{ "success": true, "data": { "savedAt": "2025-01-15T10:30:00Z" }, "message": null }
```

---

### PUT `/stories/{slug}/chapters/{number}/content` 🔒
Manual save. Persists HTML to DB and creates a version record. Author only.

**Request**
```json
{ "content": "<p>Once upon a time in a land far away...</p>" }
```

**Response 200**
```json
{ "success": true, "data": { "versionNumber": 4, "wordCount": 1240, "savedAt": "..." }, "message": null }
```

**Errors**
| Status | Code        | When                       |
|--------|-------------|----------------------------|
| 403    | FORBIDDEN   | Not the chapter author     |

---

### GET `/stories/{slug}/chapters/{number}/versions` 🔒
List version history for a chapter. Author only.

**Response 200**
```json
{
  "success": true,
  "data": [
    { "versionNumber": 4, "wordCount": 1240, "isPublished": true, "createdAt": "..." },
    { "versionNumber": 3, "wordCount": 980,  "isPublished": false, "createdAt": "..." }
  ],
  "message": null
}
```

---

### GET `/stories/{slug}/chapters/{number}/versions/{versionNumber}` 🔒
Fetch HTML content of a specific version from DB. Author only.

**Response 200**
```json
{
  "success": true,
  "data": {
    "versionNumber": 3,
    "content": "<p>Once upon a time...</p>",
    "wordCount": 980,
    "isPublished": false,
    "createdAt": "..."
  },
  "message": null
}
```

---

### DELETE `/stories/{slug}/chapters/{number}` 🔒
Delete a chapter. Author only. Decrements story `chapterCount` and `wordCount` if published.

**Response 204** — no body

---

### POST `/stories/{slug}/chapters/{number}/publish` 🔒
Publish a DRAFT chapter. Author only.

**Request** — optional, for scheduled publish
```json
{ "publishAt": "2025-02-01T08:00:00Z" }
```
If `publishAt` is omitted or in the past, publishes immediately.

**Response 200**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "chapterNumber": 3,
    "status": "PUBLISHED",
    "publishedAt": "2025-01-15T10:30:00Z"
  },
  "message": null
}
```

**Errors**
| Status | Code         | When                                    |
|--------|--------------|-----------------------------------------|
| 400    | BAD_REQUEST  | Chapter has fewer than 100 words        |
| 400    | BAD_REQUEST  | Chapter is already PUBLISHED            |
| 403    | FORBIDDEN    | Not the story author                    |

---

## 5. Comments

### GET `/stories/{slug}/chapters/{number}/comments`
Get top-level comments for a chapter. Paginated.

**Query Params**
| Param  | Type    | Default | Notes                       |
|--------|---------|---------|-----------------------------|
| page   | Integer | 0       |                             |
| size   | Integer | 20      |                             |
| sort   | String  | `newest`| `newest`, `top` (by votes)  |

**Response 200** — `PageResponse<CommentResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "user": { "id": "uuid", "username": "jane", "displayName": "Jane", "avatarUrl": "..." },
        "content": "This chapter was amazing!",
        "voteCount": 12,
        "userVote": 1,
        "replyCount": 3,
        "isDeleted": false,
        "createdAt": "2025-01-15T10:30:00Z",
        "updatedAt": "2025-01-15T10:30:00Z"
      }
    ],
    "page": 0, "size": 20, "total": 45, "totalPages": 3
  },
  "message": null
}
```

---

### GET `/comments/{id}/replies`
Get replies to a specific comment. Paginated.

**Query Params**
| Param | Type    | Default |
|-------|---------|---------|
| page  | Integer | 0       |
| size  | Integer | 20      |

**Response 200** — `PageResponse<CommentResponse>`

---

### POST `/stories/{slug}/chapters/{number}/comments` 🔒
Post a comment or reply on a chapter.

**Request**
```json
{
  "content": "This chapter was incredible!",
  "parentId": null
}
```

**Validation**
| Field    | Rules                                      |
|----------|--------------------------------------------|
| content  | `@NotBlank`, `@Size(min = 1, max = 2000)`  |
| parentId | Must be a top-level comment (no nesting beyond 1 level) |

**Response 201** — `CommentResponse` (same shape as GET item above)

**Errors**
| Status | Code               | When                                   |
|--------|--------------------|----------------------------------------|
| 400    | BAD_REQUEST        | parentId refers to a reply (depth > 1) |
| 404    | CHAPTER_NOT_FOUND  | Chapter does not exist                 |
| 404    | COMMENT_NOT_FOUND  | parentId does not exist                |

---

### PATCH `/comments/{id}` 🔒
Edit own comment. Author of comment only.

**Request**
```json
{ "content": "Updated comment text" }
```

**Response 200** — `CommentResponse`

**Errors**
| Status | Code               | When                    |
|--------|--------------------|-------------------------|
| 403    | FORBIDDEN          | Not the comment author  |
| 404    | COMMENT_NOT_FOUND  | Comment does not exist  |

---

### DELETE `/comments/{id}` 🔒
Delete own comment. Soft delete if has replies, hard delete if no replies.

**Response 204** — no body

---

### POST `/comments/{id}/vote` 🔒
Vote on a comment. Toggles off if same vote sent twice.

**Request**
```json
{ "vote": 1 }
```

**Validation**
| Field | Rules                             |
|-------|-----------------------------------|
| vote  | `@NotNull`, must be `1` or `-1`   |

**Response 200**
```json
{ "success": true, "data": { "voteCount": 13, "userVote": 1 }, "message": null }
```

---

## 6. Reading

### GET `/me/library` 🔒
Get all reading lists for the authenticated user.

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Reading",
      "isDefault": true,
      "storyCount": 5,
      "createdAt": "2025-01-01T00:00:00Z"
    }
  ],
  "message": null
}
```

---

### POST `/me/library` 🔒
Create a new reading list.

**Request**
```json
{ "name": "Favorites" }
```

**Validation**
| Field | Rules                               |
|-------|-------------------------------------|
| name  | `@NotBlank`, `@Size(min = 1, max = 100)` |

**Response 201**
```json
{ "success": true, "data": { "id": "uuid", "name": "Favorites", "isDefault": false, "storyCount": 0, "createdAt": "..." }, "message": null }
```

**Errors**
| Status | Code        | When                              |
|--------|-------------|-----------------------------------|
| 400    | BAD_REQUEST | User already has 20 reading lists |

---

### POST `/me/library/{listId}/stories/{storyId}` 🔒
Add a story to a reading list.

**Response 200**
```json
{ "success": true, "data": { "listId": "uuid", "storyId": "uuid", "addedAt": "..." }, "message": null }
```

**Errors**
| Status | Code              | When                             |
|--------|-------------------|----------------------------------|
| 403    | FORBIDDEN         | List does not belong to user     |
| 404    | LIST_NOT_FOUND    | Reading list does not exist      |
| 404    | STORY_NOT_FOUND   | Story does not exist             |
| 409    | ALREADY_IN_LIST   | Story already in this list       |

---

### DELETE `/me/library/{listId}/stories/{storyId}` 🔒
Remove a story from a reading list.

**Response 204** — no body

---

### PUT `/me/reading-progress/{storyId}` 🔒
Save or update reading progress for a story.

**Request**
```json
{
  "chapterId": "uuid",
  "progressPct": 65
}
```

**Validation**
| Field       | Rules                              |
|-------------|------------------------------------|
| chapterId   | `@NotNull`, must exist             |
| progressPct | `@Min(0)`, `@Max(100)`             |

**Response 200**
```json
{ "success": true, "data": { "storyId": "uuid", "chapterId": "uuid", "progressPct": 65, "lastReadAt": "..." }, "message": null }
```

---

### GET `/me/reading-history` 🔒
Get recently read stories, ordered by `lastReadAt` DESC.

**Query Params**
| Param | Type    | Default |
|-------|---------|---------|
| page  | Integer | 0       |
| size  | Integer | 20      |

**Response 200** — `PageResponse<ReadingHistoryResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "story": { "id": "uuid", "slug": "...", "title": "...", "coverImageUrl": "..." },
        "lastChapter": { "id": "uuid", "chapterNumber": 5, "title": "..." },
        "progressPct": 65,
        "lastReadAt": "2025-01-15T10:30:00Z"
      }
    ],
    "page": 0, "size": 20, "total": 12, "totalPages": 1
  },
  "message": null
}
```

---

## 7. Search

### GET `/search/stories`
Full-text search stories using PostgreSQL tsvector.

**Query Params**
| Param   | Type    | Required | Notes                                     |
|---------|---------|----------|-------------------------------------------|
| q       | String  | Yes      | Search term, min 2 chars                  |
| tag     | String  | No       | Filter by tag slug                        |
| status  | String  | No       | ONGOING, COMPLETED, HIATUS                |
| lang    | String  | No       | ISO 639-1 code                            |
| sort    | String  | No       | `relevance` (default), `newest`, `popular`|
| page    | Integer | No       | Default 0                                 |
| size    | Integer | No       | Default 20, max 100                       |

**Response 200** — `PageResponse<StoryCardResponse>`

**Errors**
| Status | Code        | When                   |
|--------|-------------|------------------------|
| 400    | BAD_REQUEST | `q` is shorter than 2 chars |

---

### GET `/search/users`
Search for authors by username or display name.

**Query Params**
| Param | Type    | Required | Notes         |
|-------|---------|----------|---------------|
| q     | String  | Yes      | Min 2 chars   |
| page  | Integer | No       | Default 0     |
| size  | Integer | No       | Default 20    |

**Response 200** — `PageResponse<UserCardResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "username": "johndoe",
        "displayName": "John Doe",
        "avatarUrl": "...",
        "bio": "Fantasy writer",
        "storyCount": 8,
        "followerCount": 120,
        "isFollowing": false
      }
    ],
    "page": 0, "size": 20, "total": 3, "totalPages": 1
  },
  "message": null
}
```

---

### GET `/search/tags`
Autocomplete tag search.

**Query Params**
| Param | Type   | Required | Notes       |
|-------|--------|----------|-------------|
| q     | String | Yes      | Min 1 char  |

**Response 200**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Fantasy", "slug": "fantasy" },
    { "id": 7, "name": "Fan Fiction", "slug": "fan-fiction" }
  ],
  "message": null
}
```

---

## 8. Notifications

### GET `/me/notifications` 🔒
Get notifications for the authenticated user.

**Query Params**
| Param  | Type    | Default | Notes                     |
|--------|---------|---------|---------------------------|
| page   | Integer | 0       |                           |
| size   | Integer | 20      |                           |
| unread | Boolean | false   | If true, return unread only |

**Response 200** — `PageResponse<NotificationResponse>`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "type": "NEW_CHAPTER",
        "payload": {
          "storyId": "uuid",
          "storyTitle": "The Last Kingdom",
          "storySlug": "the-last-kingdom-a1b2c3d4",
          "chapterNumber": 5,
          "chapterTitle": "The Twist",
          "authorUsername": "johndoe"
        },
        "isRead": false,
        "createdAt": "2025-01-15T10:30:00Z"
      }
    ],
    "page": 0, "size": 20, "total": 8, "totalPages": 1
  },
  "message": null
}
```

---

### PATCH `/me/notifications/read` 🔒
Mark all unread notifications as read.

**Request** — no body

**Response 200**
```json
{ "success": true, "data": { "markedRead": 8 }, "message": null }
```

---

### PATCH `/me/notifications/{id}/read` 🔒
Mark a single notification as read.

**Response 200**
```json
{ "success": true, "data": { "id": "uuid", "isRead": true }, "message": null }
```

---

## 9. Tags (Admin)

### GET `/tags`
List all tags.

**Response 200**
```json
{ "success": true, "data": [{ "id": 1, "name": "Fantasy", "slug": "fantasy" }], "message": null }
```

---
