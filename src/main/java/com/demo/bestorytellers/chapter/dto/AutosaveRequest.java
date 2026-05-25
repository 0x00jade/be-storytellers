package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.NotBlank;

public record AutosaveRequest(
    @NotBlank String content
) {}
