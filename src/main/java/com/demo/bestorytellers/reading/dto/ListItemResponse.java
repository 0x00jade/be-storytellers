package com.demo.bestorytellers.reading.dto;

import java.time.Instant;
import java.util.UUID;

public record ListItemResponse(UUID listId, UUID storyId, Instant addedAt) {}
