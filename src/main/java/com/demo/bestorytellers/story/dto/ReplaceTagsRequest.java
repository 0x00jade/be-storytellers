package com.demo.bestorytellers.story.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceTagsRequest(@Size(max = 10) List<String> tagNames) {}
