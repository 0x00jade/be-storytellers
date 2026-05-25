package com.demo.bestorytellers.story.service;

import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.common.util.SlugUtil;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.story.dto.AuthorDto;
import com.demo.bestorytellers.story.dto.CreateStoryRequest;
import com.demo.bestorytellers.story.dto.StoryCardResponse;
import com.demo.bestorytellers.story.dto.StoryDetailResponse;
import com.demo.bestorytellers.story.dto.TagResponse;
import com.demo.bestorytellers.story.dto.UpdateStoryRequest;
import com.demo.bestorytellers.story.entity.MaturityRating;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.entity.StoryStatus;
import com.demo.bestorytellers.story.entity.StoryVisibility;
import com.demo.bestorytellers.story.entity.Tag;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.story.repository.TagRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoryService {

    private final StoryRepository storyRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public StoryService(StoryRepository storyRepository, TagRepository tagRepository,
                        UserRepository userRepository, FollowRepository followRepository,
                        RedisTemplate<String, String> redisTemplate) {
        this.storyRepository = storyRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public StoryDetailResponse create(UUID userId, CreateStoryRequest request) {
        User author = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UUID newId = UUID.randomUUID();
        String slug = SlugUtil.generate(request.title(), newId);
        if (storyRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            if (storyRepository.existsBySlug(slug)) {
                throw new ConflictException("Could not generate unique slug");
            }
        }

        List<Tag> tags = Collections.emptyList();
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            tags = tagRepository.findAllByIdIn(request.tagIds());
            if (tags.size() != request.tagIds().size()) {
                throw new ValidationException("One or more tag IDs not found");
            }
        }

        MaturityRating rating = parseMaturityRating(request.maturityRating());
        Story story = new Story(author, request.title(), slug, request.description(),
            request.language(), rating);
        story.setTags(new HashSet<>(tags));
        Story saved = storyRepository.save(story);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse getBySlug(String slug, UUID currentUserId) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        if (story.getVisibility() == StoryVisibility.PRIVATE
                && !story.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Story is private");
        }
        return toDetailResponse(story);
    }

    @Transactional
    public StoryDetailResponse update(String slug, UUID userId, UpdateStoryRequest request) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        if (!story.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this story");
        }

        if (request.status() != null) {
            StoryStatus newStatus;
            try {
                newStatus = StoryStatus.valueOf(request.status());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid status: " + request.status());
            }
            validateStatusTransition(story.getStatus(), newStatus, story.getChapterCount());
            story.setStatus(newStatus);
        }
        if (request.title() != null) story.setTitle(request.title());
        if (request.description() != null) story.setDescription(request.description());
        if (request.visibility() != null) {
            try {
                story.setVisibility(StoryVisibility.valueOf(request.visibility()));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid visibility: " + request.visibility());
            }
        }
        if (request.maturityRating() != null) {
            story.setMaturityRating(parseMaturityRating(request.maturityRating()));
        }
        if (request.language() != null) story.setLanguage(request.language());
        if (request.tagIds() != null) {
            List<Tag> tags = tagRepository.findAllByIdIn(request.tagIds());
            story.setTags(new HashSet<>(tags));
        }

        Story saved = storyRepository.save(story);
        redisTemplate.delete("story:" + slug);
        return toDetailResponse(saved);
    }

    @Transactional
    public void delete(String slug, UUID userId) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        if (!story.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this story");
        }
        storyRepository.delete(story);
        redisTemplate.delete("story:" + slug);
    }

    @Transactional(readOnly = true)
    public PageResponse<StoryCardResponse> browse(String tag, String status, String lang,
                                                   String sort, boolean mature, int page, int size) {
        Pageable pageable = buildPageable(sort, page, size);
        return PageResponse.from(storyRepository.findAllPublic(pageable).map(this::toCardResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<StoryCardResponse> getFeed(UUID userId, int page, int size) {
        List<UUID> followingIds = followRepository.findFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), page, size, 0, 0);
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return PageResponse.from(
            storyRepository.findFeedForAuthors(followingIds, pageable).map(this::toCardResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<StoryCardResponse> getAuthorStories(String username, int page, int size) {
        User author = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("updatedAt").descending());
        return PageResponse.from(
            storyRepository.findPublicByAuthorId(author.getId(), pageable).map(this::toCardResponse));
    }

    @Transactional
    public List<TagResponse> replaceTags(String slug, UUID userId, List<Integer> tagIds) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        if (!story.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this story");
        }
        if (tagIds != null && tagIds.size() > 10) {
            throw new ValidationException("Max 10 tags allowed");
        }
        List<Integer> ids = tagIds != null ? tagIds : Collections.emptyList();
        List<Tag> tags = ids.isEmpty() ? Collections.emptyList() : tagRepository.findAllByIdIn(ids);
        if (tags.size() != ids.size()) {
            throw new ValidationException("One or more tag IDs not found");
        }
        story.setTags(new HashSet<>(tags));
        storyRepository.save(story);
        redisTemplate.delete("story:" + slug);
        return tags.stream()
            .map(t -> new TagResponse(t.getId(), t.getName(), t.getSlug()))
            .collect(Collectors.toList());
    }

    @Transactional
    public void applyViewCountDelta(UUID storyId, long delta) {
        storyRepository.incrementViewCount(storyId, delta);
    }

    private void validateStatusTransition(StoryStatus current, StoryStatus next, int chapterCount) {
        if (next == StoryStatus.DRAFT && chapterCount > 0) {
            throw new ValidationException("Cannot revert to DRAFT after publishing chapters");
        }
        boolean valid = switch (current) {
            case DRAFT -> next == StoryStatus.ONGOING;
            case ONGOING -> next == StoryStatus.COMPLETED || next == StoryStatus.HIATUS;
            case HIATUS -> next == StoryStatus.ONGOING;
            case COMPLETED -> next == StoryStatus.ONGOING;
        };
        if (!valid) {
            throw new ValidationException("Invalid status transition: " + current + " -> " + next);
        }
    }

    private MaturityRating parseMaturityRating(String value) {
        try {
            return MaturityRating.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid maturity rating: " + value);
        }
    }

    private Pageable buildPageable(String sort, int page, int size) {
        Sort s = switch (sort != null ? sort : "newest") {
            case "popular" -> Sort.by("viewCount").descending();
            case "updated" -> Sort.by("updatedAt").descending();
            default -> Sort.by("createdAt").descending();
        };
        return PageRequest.of(page, Math.min(size, 100), s);
    }

    public StoryDetailResponse toDetailResponse(Story story) {
        AuthorDto author = new AuthorDto(
            story.getAuthor().getId(),
            story.getAuthor().getUsername(),
            story.getAuthor().getDisplayName(),
            story.getAuthor().getAvatarUrl());
        List<TagResponse> tags = story.getTags().stream()
            .map(t -> new TagResponse(t.getId(), t.getName(), t.getSlug()))
            .collect(Collectors.toList());
        return new StoryDetailResponse(
            story.getId(), story.getSlug(), story.getTitle(), story.getDescription(),
            story.getCoverImageUrl(), author, story.getLanguage(), story.getStatus().name(),
            story.getVisibility().name(), story.getMaturityRating().name(), story.getViewCount(),
            story.getChapterCount(), story.getWordCount(), tags,
            story.getCreatedAt(), story.getUpdatedAt());
    }

    public StoryCardResponse toCardResponse(Story story) {
        AuthorDto author = new AuthorDto(
            story.getAuthor().getId(),
            story.getAuthor().getUsername(),
            story.getAuthor().getDisplayName(),
            story.getAuthor().getAvatarUrl());
        List<TagResponse> tags = story.getTags().stream()
            .map(t -> new TagResponse(t.getId(), t.getName(), t.getSlug()))
            .collect(Collectors.toList());
        return new StoryCardResponse(
            story.getId(), story.getSlug(), story.getTitle(), story.getDescription(),
            story.getCoverImageUrl(), author, story.getStatus().name(),
            story.getMaturityRating().name(), story.getLanguage(), story.getViewCount(),
            story.getChapterCount(), story.getWordCount(), tags,
            story.getUpdatedAt(), story.getCreatedAt());
    }
}
