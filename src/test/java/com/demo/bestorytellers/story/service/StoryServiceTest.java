package com.demo.bestorytellers.story.service;

import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.story.dto.CreateStoryRequest;
import com.demo.bestorytellers.story.dto.StoryDetailResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock TagRepository tagRepository;
    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock RedisTemplate<String, String> redisTemplate;

    private StoryService service() {
        return new StoryService(storyRepository, tagRepository, userRepository,
            followRepository, redisTemplate);
    }

    // --- create ---

    @Test
    void create_whenUserNotFound_thenThrowsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().create(userId, new CreateStoryRequest(
                "My Story", null, "en", "EVERYONE", null)));
    }

    @Test
    void create_whenTagIdMissing_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storyRepository.existsBySlug(any())).thenReturn(false);
        when(tagRepository.findAllByIdIn(List.of(999))).thenReturn(Collections.emptyList());

        assertThrows(ValidationException.class, () ->
            service().create(userId, new CreateStoryRequest(
                "My Story", null, "en", "EVERYONE", List.of(999))));
    }

    @Test
    void create_whenValidRequest_thenSavesStory() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        Story saved = mock(Story.class);
        User author = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(saved.getAuthor()).thenReturn(author);
        when(saved.getTags()).thenReturn(Collections.emptySet());
        when(saved.getStatus()).thenReturn(StoryStatus.DRAFT);
        when(saved.getVisibility()).thenReturn(StoryVisibility.PUBLIC);
        when(saved.getMaturityRating()).thenReturn(MaturityRating.EVERYONE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storyRepository.existsBySlug(any())).thenReturn(false);
        when(tagRepository.findAllByIdIn(any())).thenReturn(Collections.emptyList());
        when(storyRepository.save(any())).thenReturn(saved);

        StoryDetailResponse result = service().create(userId,
            new CreateStoryRequest("My Story", null, "en", "EVERYONE", Collections.emptyList()));

        assertNotNull(result);
        verify(storyRepository).save(any(Story.class));
    }

    @Test
    void create_whenInvalidMaturityRating_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storyRepository.existsBySlug(any())).thenReturn(false);
        when(tagRepository.findAllByIdIn(any())).thenReturn(Collections.emptyList());

        assertThrows(ValidationException.class, () ->
            service().create(userId, new CreateStoryRequest(
                "My Story", null, "en", "INVALID_RATING", Collections.emptyList())));
    }

    // --- update ---

    @Test
    void update_whenStoryNotFound_thenThrowsResourceNotFound() {
        when(storyRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().update("unknown-slug", UUID.randomUUID(),
                new UpdateStoryRequest(null, null, null, null, null, null, null)));
    }

    @Test
    void update_whenNotOwner_thenThrowsForbidden() {
        UUID wrongUser = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().update("test-slug", wrongUser,
                new UpdateStoryRequest(null, null, null, null, null, null, null)));
    }

    @Test
    void update_whenInvalidStatusTransitionDraftToCompleted_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(story.getStatus()).thenReturn(StoryStatus.DRAFT);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ValidationException.class, () ->
            service().update("test-slug", userId,
                new UpdateStoryRequest(null, null, "COMPLETED", null, null, null, null)));
    }

    @Test
    void update_whenRevertingToDraftWithPublishedChapters_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(story.getStatus()).thenReturn(StoryStatus.ONGOING);
        when(story.getChapterCount()).thenReturn(2);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ValidationException.class, () ->
            service().update("test-slug", userId,
                new UpdateStoryRequest(null, null, "DRAFT", null, null, null, null)));
    }

    @Test
    void update_whenValidStatusTransition_thenUpdatesStory() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(story.getStatus()).thenReturn(StoryStatus.ONGOING);
        when(story.getVisibility()).thenReturn(StoryVisibility.PUBLIC);
        when(story.getMaturityRating()).thenReturn(MaturityRating.EVERYONE);
        when(story.getTags()).thenReturn(Collections.emptySet());
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        when(storyRepository.save(story)).thenReturn(story);

        service().update("test-slug", userId,
            new UpdateStoryRequest(null, null, "COMPLETED", null, null, null, null));

        verify(story).setStatus(StoryStatus.COMPLETED);
        verify(storyRepository).save(story);
    }

    // --- delete ---

    @Test
    void delete_whenNotOwner_thenThrowsForbidden() {
        UUID wrongUser = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().delete("test-slug", wrongUser));
    }

    @Test
    void delete_whenOwner_thenDeletesAndInvalidatesCache() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(story.getSlug()).thenReturn("test-slug");
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        service().delete("test-slug", userId);

        verify(storyRepository).delete(story);
        verify(redisTemplate).delete("story:test-slug");
    }

    @Test
    void delete_whenStoryNotFound_thenThrowsResourceNotFound() {
        when(storyRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().delete("missing", UUID.randomUUID()));
    }

    // --- replaceTags ---

    @Test
    void replaceTags_whenTagCountExceeds10_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        List<Integer> tooManyTags = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

        assertThrows(ValidationException.class, () ->
            service().replaceTags("test-slug", userId, tooManyTags));
    }

    @Test
    void replaceTags_whenTagIdNotFound_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));
        when(tagRepository.findAllByIdIn(List.of(999))).thenReturn(Collections.emptyList());

        assertThrows(ValidationException.class, () ->
            service().replaceTags("test-slug", userId, List.of(999)));
    }

    // --- getBySlug ---

    @Test
    void getBySlug_whenPrivateAndNotOwner_thenThrowsForbidden() {
        UUID visitoerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Story story = mock(Story.class);
        when(story.getAuthor()).thenReturn(owner);
        when(story.getVisibility()).thenReturn(
            com.demo.bestorytellers.story.entity.StoryVisibility.PRIVATE);
        when(storyRepository.findBySlug("test-slug")).thenReturn(Optional.of(story));

        assertThrows(ForbiddenException.class, () ->
            service().getBySlug("test-slug", visitoerId));
    }
}
