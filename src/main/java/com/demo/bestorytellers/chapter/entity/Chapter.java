package com.demo.bestorytellers.chapter.entity;

import com.demo.bestorytellers.common.entity.BaseEntity;
import com.demo.bestorytellers.story.entity.Story;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chapters")
public class Chapter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "chapter_number", nullable = false)
    private int chapterNumber;

    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChapterStatus status = ChapterStatus.DRAFT;

    @Column(name = "published_at", columnDefinition = "TIMESTAMPTZ")
    private Instant publishedAt;

    // null = free chapter; non-null = premium, reader must pay to access
    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    protected Chapter() {}

    public Chapter(Story story, String title, int chapterNumber) {
        this.story = story;
        this.title = title;
        this.chapterNumber = chapterNumber;
    }

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getChapterNumber() { return chapterNumber; }
    public int getWordCount() { return wordCount; }
    public ChapterStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public BigDecimal getPrice() { return price; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public void setStatus(ChapterStatus status) { this.status = status; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
