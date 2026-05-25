package com.demo.bestorytellers.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String username,
    String displayName,
    String avatarUrl,
    String bio,
    long followerCount,
    long followingCount,
    long storyCount,
    boolean isFollowing,
    Instant createdAt
) {}
