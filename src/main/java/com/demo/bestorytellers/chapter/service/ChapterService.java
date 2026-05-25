package com.demo.bestorytellers.chapter.service;

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
import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.entity.ChapterVersion;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.chapter.repository.ChapterVersionRepository;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.common.util.S3Util;
import com.demo.bestorytellers.notification.service.NotificationService;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.entity.StoryStatus;
import com.demo.bestorytellers.story.repository.StoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final ChapterVersionRepository versionRepository;
    private final StoryRepository storyRepository;
    private final S3Util s3Util;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;

    public ChapterService(ChapterRepository chapterRepository,
                          ChapterVersionRepository versionRepository,
                          StoryRepository storyRepository,
                          S3Util s3Util,
                          RedisTemplate<String, String> redisTemplate,
                          NotificationService notificationService) {
        this.chapterRepository = chapterRepository;
        this.versionRepository = versionRepository;
        this.storyRepository = storyRepository;
        this.s3Util = s3Util;
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
    }

    @Transactional
    public ChapterResponse create(String slug, UUID userId, CreateChapterRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        int nextNumber = chapterRepository.findMaxChapterNumber(story.getId()) + 1;
        Chapter chapter = new Chapter(story, request.title(), nextNumber);
        Chapter saved = chapterRepository.save(chapter);
        return toResponse(saved, story.getSlug(), null);
    }

    @Transactional(readOnly = true)
    public ChapterResponse read(String slug, int number, UUID currentUserId) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(story.getId(), number)
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + number));

        if (chapter.getStatus() == ChapterStatus.DRAFT
                && !story.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Chapter is not published");
        }

        String content = null;
        if (chapter.getContentUrl() != null) {
            String cacheKey = "chapter:" + chapter.getId();
            content = redisTemplate.opsForValue().get(cacheKey);
            if (content == null) {
                content = s3Util.fetchContent(chapter.getContentUrl());
                redisTemplate.opsForValue().set(cacheKey, content, Duration.ofMinutes(30));
            }
        }
        redisTemplate.opsForValue().increment("story:views:" + story.getId());

        return toResponse(chapter, slug, content);
    }

    @Transactional
    public ChapterResponse update(String slug, int number, UUID userId, UpdateChapterRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        if (request.title() != null) {
            chapter.setTitle(request.title());
        }
        return toResponse(chapterRepository.save(chapter), slug, null);
    }

    @Transactional
    public AutosaveResponse autosave(String slug, int number, UUID userId, AutosaveRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        String s3Key = "drafts/" + chapter.getId() + "/" + userId + ".json";
        s3Util.uploadContent(s3Key, request.content());
        redisTemplate.opsForValue().set(
            "draft:" + chapter.getId() + ":" + userId, "exists", Duration.ofHours(24));
        return new AutosaveResponse(Instant.now());
    }

    @Transactional
    public SaveContentResponse saveContent(String slug, int number, UUID userId,
                                           SaveContentRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);

        String publishedKey = "content/" + chapter.getId() + "/published.json";
        int nextVersion = versionRepository.findMaxVersionNumber(chapter.getId()) + 1;
        String versionKey = "content/" + chapter.getId() + "/v" + nextVersion + ".json";

        s3Util.uploadContent(publishedKey, request.content());
        s3Util.uploadContent(versionKey, request.content());

        ChapterVersion version = new ChapterVersion(
            chapter, nextVersion, versionKey, request.wordCount(), userId);
        versionRepository.save(version);

        boolean wasPublished = chapter.getStatus() == ChapterStatus.PUBLISHED;
        int oldWordCount = chapter.getWordCount();
        chapter.setContentUrl(publishedKey);
        chapter.setWordCount(request.wordCount());
        chapterRepository.save(chapter);

        // Update story word count delta when chapter is already published
        if (wasPublished) {
            int delta = request.wordCount() - oldWordCount;
            if (delta != 0) {
                storyRepository.adjustWordCount(story.getId(), delta);
            }
        }

        redisTemplate.delete("chapter:" + chapter.getId());
        redisTemplate.delete("draft:" + chapter.getId() + ":" + userId);
        try {
            s3Util.deleteObject("drafts/" + chapter.getId() + "/" + userId + ".json");
        } catch (Exception ignored) {
            // Draft may not exist — safe to ignore
        }

        return new SaveContentResponse(nextVersion, request.wordCount(), Instant.now());
    }

    @Transactional
    public PublishResponse publish(String slug, int number, UUID userId, PublishRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);

        if (chapter.getStatus() == ChapterStatus.PUBLISHED) {
            throw new ValidationException("Chapter is already published");
        }
        if (chapter.getWordCount() < 100) {
            throw new ValidationException("Minimum 100 words required to publish");
        }

        Instant publishAt = (request != null && request.publishAt() != null)
            ? request.publishAt()
            : Instant.now();

        if (publishAt.isAfter(Instant.now())) {
            chapter.setStatus(ChapterStatus.SCHEDULED);
            chapter.setPublishedAt(publishAt);
            chapterRepository.save(chapter);
            return new PublishResponse(chapter.getId(), chapter.getChapterNumber(),
                "SCHEDULED", publishAt);
        }

        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setPublishedAt(Instant.now());
        chapterRepository.save(chapter);

        story.incrementChapterCount(chapter.getWordCount());
        if (story.getStatus() == StoryStatus.DRAFT) {
            story.setStatus(StoryStatus.ONGOING);
        }
        storyRepository.save(story);

        redisTemplate.delete("story:" + slug);
        notificationService.createNewChapterNotifications(
            story.getId(), chapter.getId(), story.getAuthor().getId());

        return new PublishResponse(chapter.getId(), chapter.getChapterNumber(),
            "PUBLISHED", chapter.getPublishedAt());
    }

    @Transactional
    public void delete(String slug, int number, UUID userId) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        if (chapter.getStatus() == ChapterStatus.PUBLISHED) {
            story.decrementChapterCount(chapter.getWordCount());
            storyRepository.save(story);
        }
        chapterRepository.delete(chapter);
        redisTemplate.delete("story:" + slug);
        redisTemplate.delete("chapter:" + chapter.getId());
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(String slug, int number, UUID userId) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        return versionRepository.findByChapterIdOrderByVersionNumberDesc(chapter.getId()).stream()
            .map(v -> new VersionResponse(v.getVersionNumber(), v.getWordCount(),
                v.isPublished(), v.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VersionContentResponse getVersion(String slug, int number, int versionNumber,
                                              UUID userId) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        ChapterVersion version = versionRepository
            .findByChapterIdAndVersionNumber(chapter.getId(), versionNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionNumber));
        String content = s3Util.fetchContent(version.getContentUrl());
        return new VersionContentResponse(version.getVersionNumber(), content,
            version.getWordCount(), version.isPublished(), version.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Page<ChapterSummaryResponse> list(String slug, String statusFilter,
                                              UUID currentUserId, int page, int size) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("chapterNumber").ascending());
        boolean isAuthor = currentUserId != null
            && story.getAuthor().getId().equals(currentUserId);
        Page<Chapter> chapters = isAuthor
            ? chapterRepository.findByStoryIdOrderByChapterNumberAsc(story.getId(), pageable)
            : chapterRepository.findByStoryIdAndStatusOrderByChapterNumberAsc(
                story.getId(), ChapterStatus.PUBLISHED, pageable);
        return chapters.map(c -> new ChapterSummaryResponse(
            c.getId(), c.getChapterNumber(), c.getTitle(),
            c.getWordCount(), c.getStatus().name(), c.getPublishedAt()));
    }

    private Story loadStoryAndCheckOwnership(String slug, UUID userId) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        if (!story.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this story");
        }
        return story;
    }

    private Chapter loadChapter(UUID storyId, int number) {
        return chapterRepository.findByStoryIdAndChapterNumber(storyId, number)
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + number));
    }

    private ChapterResponse toResponse(Chapter chapter, String storySlug, String content) {
        return new ChapterResponse(
            chapter.getId(), storySlug, chapter.getChapterNumber(), chapter.getTitle(),
            content, chapter.getWordCount(), chapter.getStatus().name(),
            chapter.getPublishedAt(), chapter.getCreatedAt(), chapter.getUpdatedAt());
    }
}
