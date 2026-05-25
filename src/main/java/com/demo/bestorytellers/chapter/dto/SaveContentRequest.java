package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveContentRequest(
    @NotBlank String content
) {}
