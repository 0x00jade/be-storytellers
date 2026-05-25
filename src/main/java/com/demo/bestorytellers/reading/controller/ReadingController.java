package com.demo.bestorytellers.reading.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.reading.dto.CreateListRequest;
import com.demo.bestorytellers.reading.dto.ListItemResponse;
import com.demo.bestorytellers.reading.dto.ProgressRequest;
import com.demo.bestorytellers.reading.dto.ProgressResponse;
import com.demo.bestorytellers.reading.dto.ReadingHistoryItem;
import com.demo.bestorytellers.reading.dto.ReadingListResponse;
import com.demo.bestorytellers.reading.service.ReadingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    @GetMapping("/library")
    public ResponseEntity<ApiResponse<List<ReadingListResponse>>> getLists(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(readingService.getLists(principal.getUserId())));
    }

    @PostMapping("/library")
    public ResponseEntity<ApiResponse<ReadingListResponse>> createList(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateListRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(readingService.createList(principal.getUserId(), request)));
    }

    @PostMapping("/library/{listId}/stories/{storyId}")
    public ResponseEntity<ApiResponse<ListItemResponse>> addToList(
        @PathVariable UUID listId,
        @PathVariable UUID storyId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            readingService.addToList(listId, storyId, principal.getUserId())));
    }

    @DeleteMapping("/library/{listId}/stories/{storyId}")
    public ResponseEntity<Void> removeFromList(
        @PathVariable UUID listId,
        @PathVariable UUID storyId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        readingService.removeFromList(listId, storyId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reading-progress/{storyId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> upsertProgress(
        @PathVariable UUID storyId,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ProgressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            readingService.upsertProgress(principal.getUserId(), storyId, request)));
    }

    @GetMapping("/reading-history")
    public ResponseEntity<ApiResponse<PageResponse<ReadingHistoryItem>>> getHistory(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            readingService.getHistory(principal.getUserId(), page, size)));
    }
}
