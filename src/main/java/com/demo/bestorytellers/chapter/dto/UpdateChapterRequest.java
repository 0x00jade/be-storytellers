package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.Size;

public record UpdateChapterRequest(
    @Size(min = 1, max = 255) String title
) {}
