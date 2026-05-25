package com.demo.bestorytellers.social.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "follows")
public class Follow {

    @EmbeddedId
    private FollowId id;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Follow() {}

    public Follow(UUID followerId, UUID followingId) {
        this.id = new FollowId(followerId, followingId);
        this.createdAt = Instant.now();
    }

    public FollowId getId() { return id; }
    public UUID getFollowerId() { return id.getFollowerId(); }
    public UUID getFollowingId() { return id.getFollowingId(); }
    public Instant getCreatedAt() { return createdAt; }

    @Embeddable
    public static class FollowId implements Serializable {

        @Column(name = "follower_id")
        private UUID followerId;

        @Column(name = "following_id")
        private UUID followingId;

        protected FollowId() {}

        public FollowId(UUID followerId, UUID followingId) {
            this.followerId = followerId;
            this.followingId = followingId;
        }

        public UUID getFollowerId() { return followerId; }
        public UUID getFollowingId() { return followingId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FollowId f)) return false;
            return Objects.equals(followerId, f.followerId) && Objects.equals(followingId, f.followingId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followingId);
        }
    }
}
