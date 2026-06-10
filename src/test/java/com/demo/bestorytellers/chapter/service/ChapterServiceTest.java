package com.demo.bestorytellers.chapter.service;

import com.demo.bestorytellers.chapter.dto.AutosaveRequest;
import com.demo.bestorytellers.chapter.dto.AutosaveResponse;
import com.demo.bestorytellers.chapter.dto.ChapterResponse;
import com.demo.bestorytellers.chapter.dto.CreateChapterRequest;
import com.demo.bestorytellers.chapter.dto.PublishRequest;
import com.demo.bestorytellers.chapter.dto.PublishResponse;
import com.demo.bestorytellers.chapter.dto.SaveContentRequest;
import com.demo.bestorytellers.chapter.dto.SaveContentResponse;
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
import com.demo.bestorytellers.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterServiceTest {

    @Mock ChapterRepository chapterRepository;
    @Mock ChapterVersionRepository versionRepository;
    @Mock StoryRepository storyRepository;
    @Mock HtmlUtil htmlUtil;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock NotificationService notificationService;
    @Mock ValueOperations<String, String> valueOps;

    private ChapterService service() {
        return new ChapterService(chapterRepository, versionRepository, storyRepository,
            htmlUtil, redisTemplate, notificationService);
    }

    private Story mockStory(UUID ownerId, String slug) {
        User author = mock(User.class);
        when(author.getId()).thenReturn(ownerId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(author);
        when(story.getSlug()).thenReturn(slug);
        when(story.getId()).thenReturn(UUID.randomUUID());
        return story;
    }

    // --- create ---

    @Test
    void create_whenStoryNotFound_thenThrowsResourceNotFound() {
        when(storyRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().create("missing", UUID.randomUUID(),
                new CreateChapterRequest("Chapter 1")));
    }

    @Test
    void create_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID wrongId = UUID.randomUUID();
        Story story = mockStory(ownerId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().create("test-slug", wrongId, new CreateChapterRequest("Ch 1")));
    }

    @Test
    void create_whenOwner_thenSavesChapter() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        when(chapterRepository.findMaxChapterNumber(any())).thenReturn(2);
        Chapter saved = mock(Chapter.class);
        when(saved.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapterRepository.save(any())).thenReturn(saved);

        service().create("test-slug", userId, new CreateChapterRequest("Ch 3"));

        verify(chapterRepository).save(any(Chapter.class));
    }

    // --- autosave ---

    @Test
    void autosave_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        Story story = mockStory(ownerId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().autosave("test-slug", 1, UUID.randomUUID(),
                new AutosaveRequest(null, "<p>draft</p>")));
    }

    @Test
    void autosave_whenChapterExists_thenUpdatesContentWithoutVersion() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        Chapter chapter = mock(Chapter.class);
        when(chapter.getId()).thenReturn(UUID.randomUUID());
        when(chapter.getChapterNumber()).thenReturn(1);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(htmlUtil.sanitize("<p>draft</p>")).thenReturn("<p>draft</p>");
        when(chapterRepository.save(chapter)).thenReturn(chapter);

        AutosaveResponse response = service().autosave("test-slug", 1, userId,
            new AutosaveRequest(null, "<p>draft</p>"));

        verify(chapter).setContent("<p>draft</p>");
        verify(chapterRepository).save(chapter);
        verify(versionRepository, never()).save(any());
        assertNotNull(response.savedAt());
        assertEquals(1, response.chapterNumber());
    }

    @Test
    void autosave_whenChapterNotFound_thenCreatesChapterAndSavesContent() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(story.getId()).thenReturn(storyId);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 99))
            .thenReturn(Optional.empty());
        when(chapterRepository.findMaxChapterNumber(storyId)).thenReturn(2);

        Chapter created = mock(Chapter.class);
        when(created.getId()).thenReturn(UUID.randomUUID());
        when(created.getChapterNumber()).thenReturn(3);
        when(chapterRepository.save(any(Chapter.class))).thenReturn(created);
        when(htmlUtil.sanitize("<p>new</p>")).thenReturn("<p>new</p>");

        AutosaveResponse response = service().autosave("test-slug", 99, userId,
            new AutosaveRequest("My Title", "<p>new</p>"));

        verify(chapterRepository, org.mockito.Mockito.times(2)).save(any(Chapter.class));
        assertNotNull(response.chapterId());
        assertEquals(3, response.chapterNumber());
        assertNotNull(response.savedAt());
    }

    // --- saveContent ---

    @Test
    void saveContent_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        Story story = mockStory(ownerId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().saveContent("test-slug", 1, UUID.randomUUID(),
                new SaveContentRequest("<p>content</p>")));
    }

    @Test
    void saveContent_whenOwner_thenSavesContentAndCreatesVersion() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapter.getWordCount()).thenReturn(0);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(htmlUtil.sanitize("<p>content</p>")).thenReturn("<p>content</p>");
        when(htmlUtil.countWords("<p>content</p>")).thenReturn(1);
        when(versionRepository.findMaxVersionNumber(any())).thenReturn(0);
        when(chapterRepository.save(chapter)).thenReturn(chapter);

        SaveContentResponse response = service().saveContent("test-slug", 1, userId,
            new SaveContentRequest("<p>content</p>"));

        verify(chapter).setContent("<p>content</p>");
        verify(chapter).setWordCount(1);
        verify(versionRepository).save(any(ChapterVersion.class));
        assertEquals(1, response.versionNumber());
        assertEquals(1, response.wordCount());
    }

    @Test
    void saveContent_whenPublishedChapter_thenAdjustsStoryWordCount() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(story.getId()).thenReturn(storyId);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.PUBLISHED);
        when(chapter.getWordCount()).thenReturn(100);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(htmlUtil.sanitize(any())).thenReturn("<p>new</p>");
        when(htmlUtil.countWords(any())).thenReturn(150);
        when(versionRepository.findMaxVersionNumber(any())).thenReturn(1);
        when(chapterRepository.save(chapter)).thenReturn(chapter);

        service().saveContent("test-slug", 1, userId, new SaveContentRequest("<p>new</p>"));

        verify(storyRepository).adjustWordCount(storyId, 50);
    }

    // --- publish ---

    @Test
    void publish_whenStoryNotFound_thenThrowsResourceNotFound() {
        when(storyRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().publish("missing", 1, UUID.randomUUID(), null));
    }

    @Test
    void publish_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID wrongId = UUID.randomUUID();
        Story story = mockStory(ownerId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().publish("test-slug", 1, wrongId, null));
    }

    @Test
    void publish_whenAlreadyPublished_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.PUBLISHED);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));

        assertThrows(ValidationException.class, () ->
            service().publish("test-slug", 1, userId, null));
    }

    @Test
    void publish_whenTooFewWords_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapter.getWordCount()).thenReturn(50);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));

        assertThrows(ValidationException.class, () ->
            service().publish("test-slug", 1, userId, null));
    }

    @Test
    void publish_whenFutureDate_thenSchedulesChapter() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapter.getWordCount()).thenReturn(200);
        when(chapter.getId()).thenReturn(UUID.randomUUID());
        when(chapter.getChapterNumber()).thenReturn(1);
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(chapterRepository.save(chapter)).thenReturn(chapter);

        Instant future = Instant.now().plusSeconds(3600);
        PublishResponse response = service().publish("test-slug", 1, userId,
            new PublishRequest(future));

        assertEquals("SCHEDULED", response.status());
        verify(chapter).setStatus(ChapterStatus.SCHEDULED);
        verify(storyRepository, never()).save(any());
    }

    @Test
    void publish_whenValidDraftWithEnoughWords_thenPublishesAndUpdatesStory() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        User author = mock(User.class);
        when(author.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(author);
        when(story.getSlug()).thenReturn("test-slug");
        when(story.getId()).thenReturn(storyId);
        when(story.getStatus()).thenReturn(StoryStatus.DRAFT);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapter.getWordCount()).thenReturn(150);
        when(chapter.getId()).thenReturn(UUID.randomUUID());
        when(chapter.getChapterNumber()).thenReturn(1);
        when(chapter.getPublishedAt()).thenReturn(Instant.now());
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(chapterRepository.save(chapter)).thenReturn(chapter);
        when(storyRepository.save(story)).thenReturn(story);

        PublishResponse response = service().publish("test-slug", 1, userId, null);

        assertEquals("PUBLISHED", response.status());
        verify(story).incrementChapterCount(150);
        verify(story).setStatus(StoryStatus.ONGOING);
        verify(storyRepository).save(story);
        verify(notificationService).createNewChapterNotifications(any(), any(), any());
    }

    // --- delete ---

    @Test
    void delete_whenPublishedChapter_thenDecrementsStoryCounters() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(story.getId()).thenReturn(storyId);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.PUBLISHED);
        when(chapter.getWordCount()).thenReturn(300);
        when(chapter.getId()).thenReturn(UUID.randomUUID());
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));
        when(storyRepository.save(story)).thenReturn(story);

        service().delete("test-slug", 1, userId);

        verify(story).decrementChapterCount(300);
        verify(storyRepository).save(story);
        verify(chapterRepository).delete(chapter);
    }

    @Test
    void delete_whenDraftChapter_thenDoesNotUpdateStoryCounters() {
        UUID userId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(chapter.getId()).thenReturn(UUID.randomUUID());
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1)))
            .thenReturn(Optional.of(chapter));

        service().delete("test-slug", 1, userId);

        verify(story, never()).decrementChapterCount(anyInt());
        verify(storyRepository, never()).save(any());
        verify(chapterRepository).delete(chapter);
    }

    // --- list ---

    @Test
    void list_whenAnonymousUser_thenReturnsOnlyPublished() {
        UUID storyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Story story = mockStory(ownerId, "test-slug");
        when(story.getId()).thenReturn(storyId);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Page<Chapter> emptyPage = new PageImpl<>(Collections.emptyList());
        when(chapterRepository.findByStoryIdAndStatusOrderByChapterNumberAsc(
            eq(storyId), eq(ChapterStatus.PUBLISHED), any()))
            .thenReturn(emptyPage);

        service().list("test-slug", null, null, 0, 20);

        verify(chapterRepository).findByStoryIdAndStatusOrderByChapterNumberAsc(
            eq(storyId), eq(ChapterStatus.PUBLISHED), any());
        verify(chapterRepository, never()).findByStoryIdOrderByChapterNumberAsc(any(), any());
    }

    @Test
    void list_whenOwner_thenReturnsAllChapters() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(userId, "test-slug");
        when(story.getId()).thenReturn(storyId);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        Page<Chapter> emptyPage = new PageImpl<>(Collections.emptyList());
        when(chapterRepository.findByStoryIdOrderByChapterNumberAsc(eq(storyId), any()))
            .thenReturn(emptyPage);

        service().list("test-slug", null, userId, 0, 20);

        verify(chapterRepository).findByStoryIdOrderByChapterNumberAsc(eq(storyId), any());
    }
}
