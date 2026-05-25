package com.demo.bestorytellers.reading.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProgressRequest(@NotNull UUID chapterId, @Min(0) @Max(100) int progressPct) {}
