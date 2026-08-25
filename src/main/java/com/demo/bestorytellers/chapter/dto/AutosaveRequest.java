package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.NotBlank;

public record AutosaveRequest(
    String title,
    @NotBlank String content,
    int wordCount
) {}
