package com.demo.bestorytellers.story.controller;

import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.story.dto.TagResponse;
import com.demo.bestorytellers.story.repository.TagRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<TagResponse>>> listAll() {
        List<TagResponse> tags = tagRepository.findAll().stream()
            .map(t -> new TagResponse(t.getId(), t.getName(), t.getSlug()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(tags));
    }

    @GetMapping("/search/tags")
    public ResponseEntity<ApiResponse<List<TagResponse>>> search(@RequestParam String q) {
        List<TagResponse> tags = tagRepository
            .findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(q, q).stream()
            .map(t -> new TagResponse(t.getId(), t.getName(), t.getSlug()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(tags));
    }
}
