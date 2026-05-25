package com.demo.bestorytellers.story.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryDetailResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String coverImageUrl,
    AuthorDto author,
    String language,
    String status,
    String visibility,
    String maturityRating,
    long viewCount,
    int chapterCount,
    int wordCount,
    List<TagResponse> tags,
    Instant createdAt,
    Instant updatedAt
) {}
