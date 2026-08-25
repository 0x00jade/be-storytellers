package com.demo.bestorytellers.story.entity;

import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "story_stars")
public class StoryStar {

    @EmbeddedId
    private StoryStarId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("storyId")
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected StoryStar() {}

    public StoryStar(User user, Story story) {
        this.id = new StoryStarId(user.getId(), story.getId());
        this.user = user;
        this.story = story;
        this.createdAt = Instant.now();
    }

    public StoryStarId getId() { return id; }
    public User getUser() { return user; }
    public Story getStory() { return story; }
    public Instant getCreatedAt() { return createdAt; }

    @Embeddable
    public static class StoryStarId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "story_id")
        private UUID storyId;

        protected StoryStarId() {}

        public StoryStarId(UUID userId, UUID storyId) {
            this.userId = userId;
            this.storyId = storyId;
        }

        public UUID getUserId() { return userId; }
        public UUID getStoryId() { return storyId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StoryStarId s)) return false;
            return Objects.equals(userId, s.userId) && Objects.equals(storyId, s.storyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, storyId);
        }
    }
}
