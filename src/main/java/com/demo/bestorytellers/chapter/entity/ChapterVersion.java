package com.demo.bestorytellers.chapter.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chapter_versions")
public class ChapterVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "saved_by")
    private UUID savedBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected ChapterVersion() {}

    public ChapterVersion(Chapter chapter, int versionNumber, String content,
                          int wordCount, UUID savedBy) {
        this.chapter = chapter;
        this.versionNumber = versionNumber;
        this.content = content;
        this.wordCount = wordCount;
        this.savedBy = savedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Chapter getChapter() { return chapter; }
    public int getVersionNumber() { return versionNumber; }
    public String getContent() { return content; }
    public int getWordCount() { return wordCount; }
    public boolean isPublished() { return published; }
    public UUID getSavedBy() { return savedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPublished(boolean published) { this.published = published; }
}
