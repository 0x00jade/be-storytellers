package com.demo.bestorytellers.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommentRequest(
    @NotBlank @Size(min = 1, max = 2000) String content,
    UUID parentId
) {}
