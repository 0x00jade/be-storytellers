package com.demo.bestorytellers.notification.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.notification.dto.MarkReadResponse;
import com.demo.bestorytellers.notification.dto.NotificationResponse;
import com.demo.bestorytellers.notification.dto.SingleReadResponse;
import com.demo.bestorytellers.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean unread
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificationService.getNotifications(principal.getUserId(), unread, page, size)));
    }

    @PatchMapping("/read")
    public ResponseEntity<ApiResponse<MarkReadResponse>> markAllRead(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markAllRead(principal.getUserId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<SingleReadResponse>> markOneRead(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markOneRead(id, principal.getUserId())));
    }
}
