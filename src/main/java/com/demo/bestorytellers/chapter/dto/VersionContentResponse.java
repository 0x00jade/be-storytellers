package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;

public record VersionContentResponse(
    int versionNumber,
    String content,
    int wordCount,
    boolean isPublished,
    Instant createdAt
) {}
