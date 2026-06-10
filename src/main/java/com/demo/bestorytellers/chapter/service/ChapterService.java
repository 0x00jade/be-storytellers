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
import com.demo.bestorytellers.common.util.HtmlUtil;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final ChapterVersionRepository versionRepository;
    private final StoryRepository storyRepository;
    private final HtmlUtil htmlUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;

    public ChapterService(ChapterRepository chapterRepository,
                          ChapterVersionRepository versionRepository,
                          StoryRepository storyRepository,
                          HtmlUtil htmlUtil,
                          RedisTemplate<String, String> redisTemplate,
                          NotificationService notificationService) {
        this.chapterRepository = chapterRepository;
        this.versionRepository = versionRepository;
        this.storyRepository = storyRepository;
        this.htmlUtil = htmlUtil;
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
    }

    @Transactional
    public ChapterResponse create(String slug, UUID userId, CreateChapterRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        int nextNumber = chapterRepository.findMaxChapterNumber(story.getId()) + 1;
        Chapter chapter = new Chapter(story, request.title(), nextNumber);
        Chapter saved = chapterRepository.save(chapter);
        return toResponse(saved, story.getSlug());
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

        redisTemplate.opsForValue().increment("story:views:" + story.getId());
        return toResponse(chapter, slug);
    }

    @Transactional
    public ChapterResponse update(String slug, int number, UUID userId, UpdateChapterRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);
        if (request.title() != null) {
            chapter.setTitle(request.title());
        }
        return toResponse(chapterRepository.save(chapter), slug);
    }

    @Transactional
    public AutosaveResponse autosave(String slug, int number, UUID userId, AutosaveRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(story.getId(), number)
            .orElseGet(() -> {
                int nextNumber = chapterRepository.findMaxChapterNumber(story.getId()) + 1;
                String title = (request.title() != null && !request.title().isBlank())
                    ? request.title() : "Chapter " + nextNumber;
                return chapterRepository.save(new Chapter(story, title, nextNumber));
            });
        chapter.setContent(htmlUtil.sanitize(request.content()));
        chapter.setWordCount((request.wordCount()));
        Chapter saved = chapterRepository.save(chapter);
        return new AutosaveResponse(saved.getId(), saved.getChapterNumber(), Instant.now());
    }

    @Transactional
    public SaveContentResponse saveContent(String slug, int number, UUID userId,
                                           SaveContentRequest request) {
        Story story = loadStoryAndCheckOwnership(slug, userId);
        Chapter chapter = loadChapter(story.getId(), number);

        String sanitized = htmlUtil.sanitize(request.content());
        int wordCount = htmlUtil.countWords(sanitized);

        int nextVersion = versionRepository.findMaxVersionNumber(chapter.getId()) + 1;
        ChapterVersion version = new ChapterVersion(chapter, nextVersion, sanitized, wordCount, userId);
        versionRepository.save(version);

        boolean wasPublished = chapter.getStatus() == ChapterStatus.PUBLISHED;
        int oldWordCount = chapter.getWordCount();
        chapter.setContent(sanitized);
        chapter.setWordCount(wordCount);
        chapterRepository.save(chapter);

        if (wasPublished) {
            int delta = wordCount - oldWordCount;
            if (delta != 0) {
                storyRepository.adjustWordCount(story.getId(), delta);
            }
        }

        return new SaveContentResponse(nextVersion, wordCount, Instant.now());
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
        return new VersionContentResponse(version.getVersionNumber(), version.getContent(),
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

    private ChapterResponse toResponse(Chapter chapter, String storySlug) {
        return new ChapterResponse(
            chapter.getId(), storySlug, chapter.getChapterNumber(), chapter.getTitle(),
            chapter.getContent(), chapter.getWordCount(), chapter.getStatus().name(),
            chapter.getPublishedAt(), chapter.getCreatedAt(), chapter.getUpdatedAt());
    }
}
