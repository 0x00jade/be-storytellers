package com.demo.bestorytellers.story.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCoverRequest(
    @NotBlank String coverImageUrl
) {}
