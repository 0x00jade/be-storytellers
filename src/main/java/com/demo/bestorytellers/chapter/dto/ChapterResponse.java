package com.demo.bestorytellers.chapter.dto;

import java.math.BigDecimal;
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
    BigDecimal price,
    boolean isPurchased,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt
) {}
