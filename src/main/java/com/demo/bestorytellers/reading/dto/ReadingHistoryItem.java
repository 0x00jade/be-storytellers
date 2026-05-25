package com.demo.bestorytellers.reading.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadingHistoryItem(
    StoryRef story,
    ChapterRef lastChapter,
    int progressPct,
    Instant lastReadAt
) {
    public record StoryRef(UUID id, String slug, String title, String coverImageUrl) {}
    public record ChapterRef(UUID id, int chapterNumber, String title) {}
}
