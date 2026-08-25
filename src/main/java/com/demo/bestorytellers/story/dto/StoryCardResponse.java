package com.demo.bestorytellers.story.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryCardResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String coverImageUrl,
    AuthorDto author,
    String status,
    String maturityRating,
    String language,
    long viewCount,
    int chapterCount,
    int wordCount,
    long starCount,
    boolean isStarred,
    List<TagResponse> tags,
    Instant updatedAt,
    Instant createdAt
) {}
