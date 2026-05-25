package com.demo.bestorytellers.social.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    CommentUserDto user,
    String content,
    int voteCount,
    Integer userVote,
    long replyCount,
    boolean isDeleted,
    Instant createdAt,
    Instant updatedAt
) {}
