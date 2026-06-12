package com.demo.bestorytellers.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record PresignRequest(

    @NotNull
    UploadType uploadType,

    @NotBlank
    @Pattern(regexp = "image/jpeg|image/png", message = "contentType must be image/jpeg or image/png")
    String contentType,

    @NotNull
    @Min(value = 1, message = "fileSizeBytes must be at least 1")
    @Max(value = 5_242_880, message = "File must not exceed 5MB")
    Long fileSizeBytes,

    UUID referenceId

) {
    public enum UploadType {
        AVATAR,
        STORY_COVER,
        CHAPTER_IMAGE
    }
}
