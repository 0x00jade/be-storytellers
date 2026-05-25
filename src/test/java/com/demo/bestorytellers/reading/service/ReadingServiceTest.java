package com.demo.bestorytellers.reading.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.reading.dto.CreateListRequest;
import com.demo.bestorytellers.reading.dto.ProgressRequest;
import com.demo.bestorytellers.reading.dto.ProgressResponse;
import com.demo.bestorytellers.reading.entity.ReadingList;
import com.demo.bestorytellers.reading.entity.ReadingProgress;
import com.demo.bestorytellers.reading.repository.ReadingListItemRepository;
import com.demo.bestorytellers.reading.repository.ReadingListRepository;
import com.demo.bestorytellers.reading.repository.ReadingProgressRepository;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingServiceTest {

    @Mock ReadingListRepository listRepository;
    @Mock ReadingListItemRepository itemRepository;
    @Mock ReadingProgressRepository progressRepository;
    @Mock StoryRepository storyRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock UserRepository userRepository;

    private ReadingService service() {
        return new ReadingService(listRepository, itemRepository, progressRepository,
            storyRepository, chapterRepository, userRepository);
    }

    // --- createList ---

    @Test
    void createList_whenMaxReached_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        when(listRepository.countByUserId(userId)).thenReturn(20L);

        assertThrows(ValidationException.class, () ->
            service().createList(userId, new CreateListRequest("New List")));
    }

    @Test
    void createList_whenUnderLimit_thenCreates() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(listRepository.countByUserId(userId)).thenReturn(5L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ReadingList list = mock(ReadingList.class);
        when(list.getId()).thenReturn(UUID.randomUUID());
        when(list.getName()).thenReturn("Favorites");
        when(list.isDefaultList()).thenReturn(false);
        when(list.getCreatedAt()).thenReturn(java.time.Instant.now());
        when(listRepository.save(any())).thenReturn(list);

        var result = service().createList(userId, new CreateListRequest("Favorites"));

        assertNotNull(result);
        verify(listRepository).save(any(ReadingList.class));
    }

    // --- addToList ---

    @Test
    void addToList_whenAlreadyInList_thenThrowsConflict() {
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        ReadingList list = mock(ReadingList.class);
        when(list.getUser()).thenReturn(user);
        Story story = mock(Story.class);

        when(listRepository.findById(listId)).thenReturn(Optional.of(list));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(itemRepository.existsByIdListIdAndIdStoryId(listId, storyId)).thenReturn(true);

        assertThrows(ConflictException.class, () ->
            service().addToList(listId, storyId, userId));
    }

    @Test
    void addToList_whenListNotFound_thenThrowsResourceNotFound() {
        when(listRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().addToList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void addToList_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        ReadingList list = mock(ReadingList.class);
        when(list.getUser()).thenReturn(owner);
        when(listRepository.findById(any())).thenReturn(Optional.of(list));

        assertThrows(ForbiddenException.class, () ->
            service().addToList(UUID.randomUUID(), UUID.randomUUID(), otherId));
    }

    // --- removeFromList ---

    @Test
    void removeFromList_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        ReadingList list = mock(ReadingList.class);
        when(list.getUser()).thenReturn(owner);
        when(listRepository.findById(any())).thenReturn(Optional.of(list));

        assertThrows(ForbiddenException.class, () ->
            service().removeFromList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void removeFromList_whenOwner_thenDelegates() {
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        ReadingList list = mock(ReadingList.class);
        when(list.getUser()).thenReturn(user);
        when(listRepository.findById(listId)).thenReturn(Optional.of(list));

        service().removeFromList(listId, storyId, userId);

        verify(itemRepository).deleteByIdListIdAndIdStoryId(listId, storyId);
    }

    // --- upsertProgress ---

    @Test
    void upsertProgress_whenNoExistingProgress_thenCreatesNew() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        User user = mock(User.class);
        Story story = mock(Story.class);
        Chapter chapter = mock(Chapter.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(progressRepository.findByIdUserIdAndIdStoryId(userId, storyId)).thenReturn(Optional.empty());

        ReadingProgress savedProgress = mock(ReadingProgress.class);
        when(savedProgress.getProgressPct()).thenReturn((short) 50);
        when(savedProgress.getLastReadAt()).thenReturn(java.time.Instant.now());
        when(progressRepository.save(any())).thenReturn(savedProgress);

        ProgressResponse result = service().upsertProgress(userId, storyId, new ProgressRequest(chapterId, 50));

        assertNotNull(result);
        verify(progressRepository).save(any(ReadingProgress.class));
    }

    @Test
    void upsertProgress_whenStoryNotFound_thenThrowsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));
        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().upsertProgress(userId, storyId, new ProgressRequest(UUID.randomUUID(), 50)));
    }

    @Test
    void upsertProgress_whenExistingProgress_thenUpdates() {
        UUID userId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        User user = mock(User.class);
        Story story = mock(Story.class);
        Chapter chapter = mock(Chapter.class);
        ReadingProgress existing = mock(ReadingProgress.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(progressRepository.findByIdUserIdAndIdStoryId(userId, storyId)).thenReturn(Optional.of(existing));
        when(existing.getProgressPct()).thenReturn((short) 80);
        when(existing.getLastReadAt()).thenReturn(java.time.Instant.now());
        when(progressRepository.save(existing)).thenReturn(existing);

        service().upsertProgress(userId, storyId, new ProgressRequest(chapterId, 80));

        verify(existing).update(chapter, (short) 80);
    }
}
