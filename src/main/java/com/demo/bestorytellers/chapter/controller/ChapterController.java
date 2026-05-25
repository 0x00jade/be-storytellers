package com.demo.bestorytellers.chapter.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.chapter.dto.AutosaveRequest;
import com.demo.bestorytellers.chapter.dto.AutosaveResponse;
import com.demo.bestorytellers.chapter.dto.ChapterResponse;
import com.demo.bestorytellers.chapter.dto.ChapterSummaryResponse;
import com.demo.bestorytellers.chapter.dto.CreateChapterRequest;
import com.demo.bestorytellers.chapter.dto.PublishRequest;
import com.demo.bestorytellers.chapter.dto.PublishResponse;
import com.demo.bestorytellers.chapter.dto.SaveContentRequest;
import com.demo.bestorytellers.chapter.dto.SaveContentResponse;
import com.demo.bestorytellers.chapter.dto.UpdateChapterRequest;
import com.demo.bestorytellers.chapter.dto.VersionContentResponse;
import com.demo.bestorytellers.chapter.dto.VersionResponse;
import com.demo.bestorytellers.chapter.service.ChapterService;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.dto.PageResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories/{slug}/chapters")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChapterSummaryResponse>>> list(
        @PathVariable String slug,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
            PageResponse.from(chapterService.list(slug, null, userId, page, size))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChapterResponse>> create(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateChapterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(chapterService.create(slug, principal.getUserId(), request)));
    }

    @GetMapping("/{number}")
    public ResponseEntity<ApiResponse<ChapterResponse>> read(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(chapterService.read(slug, number, userId)));
    }

    @PatchMapping("/{number}")
    public ResponseEntity<ApiResponse<ChapterResponse>> update(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateChapterRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.update(slug, number, principal.getUserId(), request)));
    }

    @DeleteMapping("/{number}")
    public ResponseEntity<Void> delete(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        chapterService.delete(slug, number, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{number}/publish")
    public ResponseEntity<ApiResponse<PublishResponse>> publish(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody(required = false) PublishRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.publish(slug, number, principal.getUserId(), request)));
    }

    @PostMapping("/{number}/autosave")
    public ResponseEntity<ApiResponse<AutosaveResponse>> autosave(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody AutosaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.autosave(slug, number, principal.getUserId(), request)));
    }

    @PutMapping("/{number}/content")
    public ResponseEntity<ApiResponse<SaveContentResponse>> saveContent(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SaveContentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.saveContent(slug, number, principal.getUserId(), request)));
    }

    @GetMapping("/{number}/versions")
    public ResponseEntity<ApiResponse<List<VersionResponse>>> listVersions(
        @PathVariable String slug,
        @PathVariable int number,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.listVersions(slug, number, principal.getUserId())));
    }

    @GetMapping("/{number}/versions/{versionNumber}")
    public ResponseEntity<ApiResponse<VersionContentResponse>> getVersion(
        @PathVariable String slug,
        @PathVariable int number,
        @PathVariable int versionNumber,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            chapterService.getVersion(slug, number, versionNumber, principal.getUserId())));
    }
}
