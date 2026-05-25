package com.demo.bestorytellers.reading.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadingListResponse(UUID id, String name, boolean isDefault, long storyCount, Instant createdAt) {}
