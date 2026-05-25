package com.demo.bestorytellers.chapter.dto;

import java.time.Instant;

public record SaveContentResponse(int versionNumber, int wordCount, Instant savedAt) {}
