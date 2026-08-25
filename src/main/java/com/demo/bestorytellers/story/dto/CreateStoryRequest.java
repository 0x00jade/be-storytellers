package com.demo.bestorytellers.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStoryRequest(
    @NotBlank @Size(min = 3, max = 255) String title,
    @Size(max = 2000) String description,
    @NotBlank String language,
    @NotNull String maturityRating,
    @Size(max = 10) List<String> tagNames
) {}
