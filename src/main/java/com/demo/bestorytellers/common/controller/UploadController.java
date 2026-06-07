package com.demo.bestorytellers.common.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PresignRequest;
import com.demo.bestorytellers.common.dto.PresignResponse;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.common.util.S3Util;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final int PRESIGN_EXPIRY_SECONDS = 300;

    private final S3Util s3Util;

    public UploadController(S3Util s3Util) {
        this.s3Util = s3Util;
    }

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PresignRequest request
    ) {
        String key = buildKey(request, principal.getUserId());
        String uploadUrl = s3Util.generatePresignedUploadUrl(key, request.contentType(), Duration.ofSeconds(PRESIGN_EXPIRY_SECONDS));
        String objectUrl = s3Util.buildObjectUrl(key);
        return ResponseEntity.ok(ApiResponse.ok(new PresignResponse(uploadUrl, objectUrl, key, PRESIGN_EXPIRY_SECONDS)));
    }

    private String buildKey(PresignRequest request, UUID userId) {
        String ext = "image/png".equals(request.contentType()) ? "png" : "jpg";
        return switch (request.uploadType()) {
            case AVATAR -> "avatars/%s/%s.%s".formatted(userId, UUID.randomUUID(), ext);
            case STORY_COVER -> {
                if (request.referenceId() == null) {
                    throw new ValidationException("referenceId (storyId) is required for STORY_COVER");
                }
                yield "covers/%s/%s.%s".formatted(request.referenceId(), UUID.randomUUID(), ext);
            }
        };
    }
}
