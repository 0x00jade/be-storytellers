package com.demo.bestorytellers.notification.service;

import com.demo.bestorytellers.notification.entity.Notification;
import com.demo.bestorytellers.notification.entity.NotificationType;
import com.demo.bestorytellers.notification.repository.NotificationRepository;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.story.entity.MaturityRating;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.entity.StoryStatus;
import com.demo.bestorytellers.story.entity.StoryVisibility;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock StoryRepository storyRepository;
    @Mock FollowRepository followRepository;

    private NotificationService service;

    private final UUID authorId = UUID.randomUUID();
    private final UUID storyId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final UUID follower1Id = UUID.randomUUID();
    private final UUID follower2Id = UUID.randomUUID();

    private User author;
    private Story story;
    private User follower1;
    private User follower2;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository,
            storyRepository, followRepository, new ObjectMapper());

        author = new User("author@test.com", "author", "Author", null, "GOOGLE", "sub-author");
        ReflectionTestUtils.setField(author, "id", authorId);

        story = new Story(author, "Test Story", "test-story-slug", "desc", "en", MaturityRating.EVERYONE);
        ReflectionTestUtils.setField(story, "id", storyId);

        follower1 = new User("f1@test.com", "follower1", "Follower One", null, "GOOGLE", "sub-f1");
        ReflectionTestUtils.setField(follower1, "id", follower1Id);

        follower2 = new User("f2@test.com", "follower2", "Follower Two", null, "GOOGLE", "sub-f2");
        ReflectionTestUtils.setField(follower2, "id", follower2Id);
    }

    @Test
    void createNewChapterNotifications_batchLoadsUsersOnce() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of(follower1Id, follower2Id));
        given(userRepository.findByIdInAndIsActiveTrue(anyCollection())).willReturn(List.of(follower1, follower2));

        service.createNewChapterNotifications(storyId, chapterId, authorId);

        then(userRepository).should(never()).findById(any());
        then(userRepository).should().findByIdInAndIsActiveTrue(anyCollection());
    }

    @Test
    void createNewChapterNotifications_batchSavesAllNotifications() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of(follower1Id, follower2Id));
        given(userRepository.findByIdInAndIsActiveTrue(anyCollection())).willReturn(List.of(follower1, follower2));

        service.createNewChapterNotifications(storyId, chapterId, authorId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationRepository).should().saveAll(captor.capture());
        then(notificationRepository).should(never()).save(any());

        List<Notification> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.NEW_CHAPTER);
    }

    @Test
    void createNewChapterNotifications_excludesAuthorFromRecipients() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of(authorId, follower1Id));
        given(userRepository.findByIdInAndIsActiveTrue(anyCollection())).willReturn(List.of(follower1));

        service.createNewChapterNotifications(storyId, chapterId, authorId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUser().getId()).isEqualTo(follower1Id);
    }

    @Test
    void createNewChapterNotifications_whenNoFollowers_doesNothing() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of());

        service.createNewChapterNotifications(storyId, chapterId, authorId);

        then(userRepository).shouldHaveNoInteractions();
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    void createNewChapterNotifications_whenStoryNotFound_doesNothing() {
        given(storyRepository.findById(storyId)).willReturn(Optional.empty());

        service.createNewChapterNotifications(storyId, chapterId, authorId);

        then(followRepository).shouldHaveNoInteractions();
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    void createStoryCompleteNotification_batchSavesAllNotifications() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of(follower1Id, follower2Id));
        given(userRepository.findByIdInAndIsActiveTrue(anyCollection())).willReturn(List.of(follower1, follower2));

        service.createStoryCompleteNotification(storyId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationRepository).should().saveAll(captor.capture());
        then(notificationRepository).should(never()).save(any());

        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allMatch(n -> n.getType() == NotificationType.STORY_COMPLETE);
    }

    @Test
    void createStoryCompleteNotification_whenNoFollowers_doesNothing() {
        given(storyRepository.findById(storyId)).willReturn(Optional.of(story));
        given(followRepository.findFollowingIds(authorId)).willReturn(List.of());

        service.createStoryCompleteNotification(storyId);

        then(notificationRepository).shouldHaveNoInteractions();
    }
}
