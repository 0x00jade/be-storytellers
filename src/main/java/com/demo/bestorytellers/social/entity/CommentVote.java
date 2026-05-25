package com.demo.bestorytellers.social.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "comment_votes")
public class CommentVote {

    @EmbeddedId
    private CommentVoteId id;

    @Column(name = "vote", nullable = false)
    private short vote;

    protected CommentVote() {}

    public CommentVote(UUID commentId, UUID userId, short vote) {
        this.id = new CommentVoteId(commentId, userId);
        this.vote = vote;
    }

    public UUID getCommentId() { return id.getCommentId(); }
    public UUID getUserId() { return id.getUserId(); }
    public short getVote() { return vote; }
    public void setVote(short vote) { this.vote = vote; }

    @Embeddable
    public static class CommentVoteId implements Serializable {

        @Column(name = "comment_id")
        private UUID commentId;

        @Column(name = "user_id")
        private UUID userId;

        protected CommentVoteId() {}

        public CommentVoteId(UUID commentId, UUID userId) {
            this.commentId = commentId;
            this.userId = userId;
        }

        public UUID getCommentId() { return commentId; }
        public UUID getUserId() { return userId; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CommentVoteId c)) return false;
            return Objects.equals(commentId, c.commentId) && Objects.equals(userId, c.userId);
        }

        @Override
        public int hashCode() { return Objects.hash(commentId, userId); }
    }
}
