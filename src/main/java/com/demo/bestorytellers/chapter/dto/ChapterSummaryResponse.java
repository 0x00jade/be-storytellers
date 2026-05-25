package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;
import java.util.UUID;

public record ChapterSummaryResponse(
    UUID id,
    int chapterNumber,
    String title,
    int wordCount,
    String status,
    Instant publishedAt
) {}
