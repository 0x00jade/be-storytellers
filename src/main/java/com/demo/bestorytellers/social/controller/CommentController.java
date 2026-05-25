package com.demo.bestorytellers.social.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.social.dto.CommentResponse;
import com.demo.bestorytellers.social.dto.CreateCommentRequest;
import com.demo.bestorytellers.social.dto.UpdateCommentRequest;
import com.demo.bestorytellers.social.dto.VoteRequest;
import com.demo.bestorytellers.social.dto.VoteResponse;
import com.demo.bestorytellers.social.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/stories/{slug}/chapters/{number}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> listTopLevel(
        @PathVariable String slug,
        @PathVariable int number,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
            commentService.listTopLevel(slug, number, sort, userId, page, size)));
    }

    @GetMapping("/comments/{id}/replies")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> listReplies(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(commentService.listReplies(id, userId, page, size)));
    }

    @PostMapping("/stories/{slug}/chapters/{number}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(commentService.create(slug, number, principal.getUserId(), request)));
    }

    @PatchMapping("/comments/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateCommentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.update(id, principal.getUserId(), request)));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        commentService.delete(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{id}/vote")
    public ResponseEntity<ApiResponse<VoteResponse>> vote(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody VoteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.vote(id, principal.getUserId(), request.vote())));
    }
}
