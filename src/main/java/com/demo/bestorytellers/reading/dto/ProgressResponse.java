package com.demo.bestorytellers.reading.dto;

import java.time.Instant;
import java.util.UUID;

public record ProgressResponse(UUID storyId, UUID chapterId, int progressPct, Instant lastReadAt) {}
