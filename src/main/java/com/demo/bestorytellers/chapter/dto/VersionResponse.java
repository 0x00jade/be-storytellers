package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;

public record VersionResponse(int versionNumber, int wordCount, boolean isPublished, Instant createdAt) {}
