package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;
import java.util.UUID;

public record AutosaveResponse(UUID chapterId, int chapterNumber, Instant savedAt) {}
