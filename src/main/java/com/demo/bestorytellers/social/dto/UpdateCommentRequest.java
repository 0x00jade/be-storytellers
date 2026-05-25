package com.demo.bestorytellers.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(@NotBlank @Size(min = 1, max = 2000) String content) {}
