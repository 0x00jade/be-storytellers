-- Denormalized star counter on stories
ALTER TABLE stories ADD COLUMN star_count BIGINT NOT NULL DEFAULT 0 CHECK (star_count >= 0);

-- One star per user per story
CREATE TABLE story_stars (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id   UUID        NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, story_id)
);

CREATE INDEX idx_story_stars_story ON story_stars(story_id);
CREATE INDEX idx_story_stars_user  ON story_stars(user_id);
