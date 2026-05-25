package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AutosaveRequest(
    @NotBlank String content,
    @Min(0) int wordCount
) {}
