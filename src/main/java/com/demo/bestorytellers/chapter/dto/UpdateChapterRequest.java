package com.demo.bestorytellers.chapter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateChapterRequest(
    @Size(min = 1, max = 255) String title,
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01") BigDecimal price,
    Boolean removePrice
) {}
