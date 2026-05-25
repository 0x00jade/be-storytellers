package com.demo.bestorytellers.user.dto;

import java.util.UUID;

public record UserCardResponse(
    UUID id,
    String username,
    String displayName,
    String avatarUrl,
    String bio,
    long storyCount,
    long followerCount,
    boolean isFollowing
) {}
