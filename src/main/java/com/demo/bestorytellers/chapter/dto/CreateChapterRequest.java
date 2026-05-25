package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChapterRequest(
    @NotBlank @Size(min = 1, max = 255) String title
) {}
