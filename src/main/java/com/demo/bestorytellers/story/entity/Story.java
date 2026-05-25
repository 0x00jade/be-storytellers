package com.demo.bestorytellers.story.entity;

import com.demo.bestorytellers.common.entity.BaseEntity;
import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "stories")
public class Story extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "language", nullable = false, length = 10)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoryStatus status = StoryStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private StoryVisibility visibility = StoryVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "maturity_rating", nullable = false, length = 20)
    private MaturityRating maturityRating = MaturityRating.EVERYONE;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;

    @Column(name = "chapter_count", nullable = false)
    private int chapterCount = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "story_tags",
        joinColumns = @JoinColumn(name = "story_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    protected Story() {}

    public Story(User author, String title, String slug, String description,
                 String language, MaturityRating maturityRating) {
        this.author = author;
        this.title = title;
        this.slug = slug;
        this.description = description;
        this.language = language;
        this.maturityRating = maturityRating;
    }

    public UUID getId() { return id; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getLanguage() { return language; }
    public StoryStatus getStatus() { return status; }
    public StoryVisibility getVisibility() { return visibility; }
    public MaturityRating getMaturityRating() { return maturityRating; }
    public long getViewCount() { return viewCount; }
    public int getWordCount() { return wordCount; }
    public int getChapterCount() { return chapterCount; }
    public Set<Tag> getTags() { return tags; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public void setLanguage(String language) { this.language = language; }
    public void setStatus(StoryStatus status) { this.status = status; }
    public void setVisibility(StoryVisibility visibility) { this.visibility = visibility; }
    public void setMaturityRating(MaturityRating maturityRating) { this.maturityRating = maturityRating; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }

    public void incrementChapterCount(int wordCountDelta) {
        this.chapterCount++;
        this.wordCount += wordCountDelta;
    }

    public void decrementChapterCount(int wordCountDelta) {
        this.chapterCount--;
        this.wordCount -= wordCountDelta;
    }

    public void addViewCount(long delta) {
        this.viewCount += delta;
    }
}
