package com.demo.bestorytellers.social.dto;

import java.util.UUID;

public record CommentUserDto(UUID id, String username, String displayName, String avatarUrl) {}
