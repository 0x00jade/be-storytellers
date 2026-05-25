package com.demo.bestorytellers.search.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.search.service.SearchService;
import com.demo.bestorytellers.story.dto.StoryCardResponse;
import com.demo.bestorytellers.user.dto.UserCardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/stories")
    public ResponseEntity<ApiResponse<PageResponse<StoryCardResponse>>> searchStories(
        @RequestParam String q,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String lang,
        @RequestParam(defaultValue = "relevance") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            searchService.searchStories(q, tag, status, lang, sort, page, size)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserCardResponse>>> searchUsers(
        @RequestParam String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
            searchService.searchUsers(q, userId, page, size)));
    }
}
