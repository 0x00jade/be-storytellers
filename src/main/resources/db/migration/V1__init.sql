-- USERS
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100),
    avatar_url    TEXT,
    bio           TEXT,
    provider      VARCHAR(20)  NOT NULL DEFAULT 'GOOGLE',
    provider_id   VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_users_email    ON users(email);
CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE INDEX        idx_users_provider ON users(provider, provider_id);
ALTER TABLE users ADD CONSTRAINT uq_users_provider UNIQUE (provider, provider_id);

-- STORIES
CREATE TABLE stories (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    description      TEXT,
    cover_image_url  TEXT,
    language         VARCHAR(10)  NOT NULL DEFAULT 'en',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    visibility       VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    maturity_rating  VARCHAR(20)  NOT NULL DEFAULT 'EVERYONE',
    view_count       BIGINT       NOT NULL DEFAULT 0,
    word_count       INT          NOT NULL DEFAULT 0,
    chapter_count    INT          NOT NULL DEFAULT 0,
    search_vector    TSVECTOR,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX        idx_stories_author     ON stories(author_id);
CREATE UNIQUE INDEX idx_stories_slug       ON stories(slug);
CREATE INDEX        idx_stories_status     ON stories(status);
CREATE INDEX        idx_stories_visibility ON stories(visibility, status);
CREATE INDEX        idx_stories_search     ON stories USING GIN(search_vector);
CREATE INDEX        idx_stories_view_count ON stories(view_count DESC);
CREATE INDEX        idx_stories_created_at ON stories(created_at DESC);

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
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id       UUID         NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    content_url    TEXT,
    content_format VARCHAR(20)  NOT NULL DEFAULT 'DELTA',
    chapter_number INT          NOT NULL,
    word_count     INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chapter_number UNIQUE (story_id, chapter_number)
);
CREATE INDEX idx_chapters_story     ON chapters(story_id);
CREATE INDEX idx_chapters_published ON chapters(story_id, status, chapter_number);
CREATE INDEX idx_chapters_status    ON chapters(status, published_at);

-- CHAPTER VERSIONS
CREATE TABLE chapter_versions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id     UUID         NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    version_number INT          NOT NULL,
    content_url    TEXT         NOT NULL,
    word_count     INT          NOT NULL,
    is_published   BOOLEAN      NOT NULL DEFAULT false,
    saved_by       UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_version_number UNIQUE (chapter_id, version_number)
);
CREATE INDEX idx_chapter_versions_chapter ON chapter_versions(chapter_id, version_number DESC);

-- TAGS
CREATE TABLE tags (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    slug VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX idx_tags_name ON tags(name);
CREATE UNIQUE INDEX idx_tags_slug ON tags(slug);

-- STORY TAGS
CREATE TABLE story_tags (
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    tag_id   INT  NOT NULL REFERENCES tags(id)    ON DELETE CASCADE,
    PRIMARY KEY (story_id, tag_id)
);

-- COMMENTS
CREATE TABLE comments (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID         NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    parent_id  UUID                  REFERENCES comments(id) ON DELETE CASCADE,
    content    TEXT         NOT NULL,
    vote_count INT          NOT NULL DEFAULT 0,
    is_deleted BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comments_chapter ON comments(chapter_id, parent_id, created_at);
CREATE INDEX idx_comments_user    ON comments(user_id);
CREATE INDEX idx_comments_parent  ON comments(parent_id);

-- COMMENT VOTES
CREATE TABLE comment_votes (
    comment_id UUID     NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id    UUID     NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    vote       SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    PRIMARY KEY (comment_id, user_id)
);

-- FOLLOWS
CREATE TABLE follows (
    follower_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id != following_id)
);
CREATE INDEX idx_follows_follower  ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);

-- READING LISTS
CREATE TABLE reading_lists (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reading_lists_user    ON reading_lists(user_id);
CREATE UNIQUE INDEX idx_reading_lists_default ON reading_lists(user_id) WHERE is_default = true;

-- READING LIST ITEMS
CREATE TABLE reading_list_items (
    list_id  UUID        NOT NULL REFERENCES reading_lists(id) ON DELETE CASCADE,
    story_id UUID        NOT NULL REFERENCES stories(id)       ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (list_id, story_id)
);

-- READING PROGRESS
CREATE TABLE reading_progress (
    user_id      UUID     NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    story_id     UUID     NOT NULL REFERENCES stories(id)  ON DELETE CASCADE,
    chapter_id   UUID              REFERENCES chapters(id) ON DELETE SET NULL,
    progress_pct SMALLINT NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_reading_progress_user  ON reading_progress(user_id, last_read_at DESC);
CREATE INDEX idx_reading_progress_story ON reading_progress(story_id);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}',
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
