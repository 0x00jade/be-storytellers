package com.demo.bestorytellers.story.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.story.dto.CoverImageResponse;
import com.demo.bestorytellers.story.dto.CreateStoryRequest;
import com.demo.bestorytellers.story.dto.ReplaceTagsRequest;
import com.demo.bestorytellers.story.dto.StoryCardResponse;
import com.demo.bestorytellers.story.dto.UpdateCoverRequest;
import com.demo.bestorytellers.story.dto.StoryDetailResponse;
import com.demo.bestorytellers.story.dto.StarResponse;
import com.demo.bestorytellers.story.dto.TagResponse;
import com.demo.bestorytellers.story.dto.UpdateStoryRequest;
import com.demo.bestorytellers.story.service.StoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping("/stories")
    public ResponseEntity<ApiResponse<PageResponse<StoryCardResponse>>> browse(
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String lang,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "false") boolean mature,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
            storyService.browse(tag, status, lang, sort, mature, page, size, userId)));
    }

    @PostMapping("/stories")
    public ResponseEntity<ApiResponse<StoryDetailResponse>> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateStoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(storyService.create(principal.getUserId(), request)));
    }

    @GetMapping("/stories/{slug}")
    public ResponseEntity<ApiResponse<StoryDetailResponse>> getBySlug(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(storyService.getBySlug(slug, userId)));
    }

    @PatchMapping("/stories/{slug}")
    public ResponseEntity<ApiResponse<StoryDetailResponse>> update(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateStoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            storyService.update(slug, principal.getUserId(), request)));
    }

    @DeleteMapping("/stories/{slug}")
    public ResponseEntity<Void> delete(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        storyService.delete(slug, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories/{slug}/cover")
    public ResponseEntity<ApiResponse<CoverImageResponse>> uploadCover(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            storyService.uploadCover(slug, principal.getUserId(), file)));
    }

    @PatchMapping("/stories/{slug}/cover")
    public ResponseEntity<ApiResponse<CoverImageResponse>> updateCover(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateCoverRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            storyService.updateCover(slug, principal.getUserId(), request.coverImageUrl())));
    }
    

    @PutMapping("/stories/{slug}/tags")
    public ResponseEntity<ApiResponse<List<TagResponse>>> replaceTags(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ReplaceTagsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            storyService.replaceTags(slug, principal.getUserId(), request.tagNames())));
    }

    @GetMapping("/users/{username}/stories")
    public ResponseEntity<ApiResponse<PageResponse<StoryCardResponse>>> getAuthorStories(
        @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(storyService.getAuthorStories(username, page, size, userId)));
    }

    @PostMapping("/stories/{slug}/star")
    public ResponseEntity<ApiResponse<StarResponse>> star(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(storyService.star(slug, principal.getUserId())));
    }

    @DeleteMapping("/stories/{slug}/star")
    public ResponseEntity<ApiResponse<StarResponse>> unstar(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(storyService.unstar(slug, principal.getUserId())));
    }

    @GetMapping("/users/me/feed")
    public ResponseEntity<ApiResponse<PageResponse<StoryCardResponse>>> getFeed(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(storyService.getFeed(principal.getUserId(), page, size)));
    }
}
