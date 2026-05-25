package com.demo.bestorytellers.story.dto;

import java.util.UUID;

public record AuthorDto(UUID id, String username, String displayName, String avatarUrl) {}
