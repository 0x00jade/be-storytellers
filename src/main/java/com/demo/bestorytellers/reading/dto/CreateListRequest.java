package com.demo.bestorytellers.reading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateListRequest(@NotBlank @Size(min = 1, max = 100) String name) {}
