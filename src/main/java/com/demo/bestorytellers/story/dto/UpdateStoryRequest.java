package com.demo.bestorytellers.story.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateStoryRequest(
    @Size(min = 3, max = 255) String title,
    @Size(max = 2000) String description,
    String status,
    String visibility,
    String maturityRating,
    String language,
    @Size(max = 10) List<Integer> tagIds
) {}
