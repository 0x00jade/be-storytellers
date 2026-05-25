package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;
import java.util.UUID;

public record ChapterResponse(
    UUID id,
    String storySlug,
    int chapterNumber,
    String title,
    String content,
    int wordCount,
    String status,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt
) {}
