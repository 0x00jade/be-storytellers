package com.demo.bestorytellers.social.entity;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.common.entity.BaseEntity;
import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "vote_count", nullable = false)
    private int voteCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    protected Comment() {}

    public Comment(Chapter chapter, User user, Comment parent, String content) {
        this.chapter = chapter;
        this.user = user;
        this.parent = parent;
        this.content = content;
    }

    public UUID getId() { return id; }
    public Chapter getChapter() { return chapter; }
    public User getUser() { return user; }
    public Comment getParent() { return parent; }
    public String getContent() { return content; }
    public int getVoteCount() { return voteCount; }
    public boolean isDeleted() { return deleted; }

    public void setContent(String content) { this.content = content; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void adjustVoteCount(int delta) { this.voteCount += delta; }
}
