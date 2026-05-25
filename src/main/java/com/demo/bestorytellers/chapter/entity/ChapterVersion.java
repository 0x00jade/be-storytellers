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

    @Column(name = "content_url", nullable = false, columnDefinition = "TEXT")
    private String contentUrl;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "saved_by")
    private UUID savedBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected ChapterVersion() {}

    public ChapterVersion(Chapter chapter, int versionNumber, String contentUrl,
                          int wordCount, UUID savedBy) {
        this.chapter = chapter;
        this.versionNumber = versionNumber;
        this.contentUrl = contentUrl;
        this.wordCount = wordCount;
        this.savedBy = savedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Chapter getChapter() { return chapter; }
    public int getVersionNumber() { return versionNumber; }
    public String getContentUrl() { return contentUrl; }
    public int getWordCount() { return wordCount; }
    public boolean isPublished() { return published; }
    public UUID getSavedBy() { return savedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPublished(boolean published) { this.published = published; }
}
