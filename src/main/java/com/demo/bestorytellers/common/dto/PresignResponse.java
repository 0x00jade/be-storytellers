package com.demo.bestorytellers.common.dto;

public record PresignResponse(
    String uploadUrl,
    String objectUrl,
    String key,
    int expiresIn
) {}
