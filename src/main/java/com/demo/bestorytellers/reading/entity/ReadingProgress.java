package com.demo.bestorytellers.reading.entity;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
public class ReadingProgress {

    @EmbeddedId
    private ReadingProgressId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("storyId")
    @JoinColumn(name = "story_id")
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct = 0;

    @Column(name = "last_read_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant lastReadAt;

    protected ReadingProgress() {}

    public ReadingProgress(User user, Story story, Chapter chapter, short progressPct) {
        this.id = new ReadingProgressId(user.getId(), story.getId());
        this.user = user;
        this.story = story;
        this.chapter = chapter;
        this.progressPct = progressPct;
        this.lastReadAt = Instant.now();
    }

    public UUID getUserId() { return id.getUserId(); }
    public UUID getStoryId() { return id.getStoryId(); }
    public User getUser() { return user; }
    public Story getStory() { return story; }
    public Chapter getChapter() { return chapter; }
    public short getProgressPct() { return progressPct; }
    public Instant getLastReadAt() { return lastReadAt; }

    public void update(Chapter chapter, short progressPct) {
        this.chapter = chapter;
        this.progressPct = progressPct;
        this.lastReadAt = Instant.now();
    }

    @Embeddable
    public static class ReadingProgressId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "story_id")
        private UUID storyId;

        protected ReadingProgressId() {}

        public ReadingProgressId(UUID userId, UUID storyId) {
            this.userId = userId;
            this.storyId = storyId;
        }

        public UUID getUserId() { return userId; }
        public UUID getStoryId() { return storyId; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReadingProgressId r)) return false;
            return Objects.equals(userId, r.userId) && Objects.equals(storyId, r.storyId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, storyId); }
    }
}
