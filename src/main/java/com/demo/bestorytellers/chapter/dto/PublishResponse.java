package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;
import java.util.UUID;

public record PublishResponse(UUID id, int chapterNumber, String status, Instant publishedAt) {}
