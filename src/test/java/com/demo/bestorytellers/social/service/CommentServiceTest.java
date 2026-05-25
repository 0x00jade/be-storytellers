package com.demo.bestorytellers.social.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.notification.service.NotificationService;
import com.demo.bestorytellers.social.dto.CreateCommentRequest;
import com.demo.bestorytellers.social.dto.UpdateCommentRequest;
import com.demo.bestorytellers.social.dto.VoteResponse;
import com.demo.bestorytellers.social.entity.Comment;
import com.demo.bestorytellers.social.entity.CommentVote;
import com.demo.bestorytellers.social.repository.CommentRepository;
import com.demo.bestorytellers.social.repository.CommentVoteRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock CommentVoteRepository voteRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock StoryRepository storyRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;

    private CommentService service() {
        return new CommentService(commentRepository, voteRepository, chapterRepository,
            storyRepository, userRepository, notificationService);
    }

    // --- create ---

    @Test
    void create_whenChapterNotPublished_thenThrowsForbidden() {
        Story story = mock(Story.class);
        Chapter chapter = mock(Chapter.class);
        when(chapter.getStatus()).thenReturn(ChapterStatus.DRAFT);
        when(storyRepository.findBySlug("slug")).thenReturn(Optional.of(story));
        when(story.getId()).thenReturn(UUID.randomUUID());
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1))).thenReturn(Optional.of(chapter));

        assertThrows(ForbiddenException.class, () ->
            service().create("slug", 1, UUID.randomUUID(), new CreateCommentRequest("text", null)));
    }

    @Test
    void create_whenParentIsReply_thenThrowsValidation() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Story story = mock(Story.class);
        Chapter chapter = mock(Chapter.class);
        Comment parentComment = mock(Comment.class);
        Comment grandParent = mock(Comment.class);
        User user = mock(User.class);

        when(story.getId()).thenReturn(UUID.randomUUID());
        when(chapter.getStatus()).thenReturn(ChapterStatus.PUBLISHED);
        when(parentComment.getParent()).thenReturn(grandParent);

        when(storyRepository.findBySlug("slug")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(1))).thenReturn(Optional.of(chapter));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(parentComment));

        assertThrows(ValidationException.class, () ->
            service().create("slug", 1, userId, new CreateCommentRequest("text", parentId)));
    }

    @Test
    void create_whenStoryNotFound_thenThrowsResourceNotFound() {
        when(storyRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
            service().create("missing", 1, UUID.randomUUID(), new CreateCommentRequest("text", null)));
    }

    @Test
    void create_whenChapterNotFound_thenThrowsResourceNotFound() {
        Story story = mock(Story.class);
        when(story.getId()).thenReturn(UUID.randomUUID());
        when(storyRepository.findBySlug("slug")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(any(), eq(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            service().create("slug", 99, UUID.randomUUID(), new CreateCommentRequest("text", null)));
    }

    // --- update ---

    @Test
    void update_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Comment comment = mock(Comment.class);
        when(comment.getUser()).thenReturn(owner);
        when(commentRepository.findById(any())).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () ->
            service().update(UUID.randomUUID(), otherId, new UpdateCommentRequest("new text")));
    }

    @Test
    void update_whenOwner_thenUpdatesContent() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("user");
        when(user.getDisplayName()).thenReturn("User");

        Comment comment = new Comment(mock(Chapter.class), user, null, "old");
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentRepository.countReplies(any())).thenReturn(0L);
        when(voteRepository.findByIdCommentIdAndIdUserId(any(), any())).thenReturn(Optional.empty());

        var result = service().update(commentId, userId, new UpdateCommentRequest("new text"));
        assertEquals("new text", result.content());
    }

    // --- delete ---

    @Test
    void delete_whenOwnerAndNoReplies_thenHardDeletes() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(commentId);
        when(comment.getUser()).thenReturn(user);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.countReplies(commentId)).thenReturn(0L);

        service().delete(commentId, userId);

        verify(commentRepository).delete(comment);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void delete_whenOwnerAndHasReplies_thenSoftDeletes() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Comment comment = mock(Comment.class);
        when(comment.getUser()).thenReturn(user);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.countReplies(commentId)).thenReturn(2L);
        when(commentRepository.save(comment)).thenReturn(comment);

        service().delete(commentId, userId);

        verify(comment).setDeleted(true);
        verify(comment).setContent("[deleted]");
        verify(commentRepository).save(comment);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void delete_whenNotOwner_thenThrowsForbidden() {
        UUID ownerId = UUID.randomUUID();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Comment comment = mock(Comment.class);
        when(comment.getUser()).thenReturn(owner);
        when(commentRepository.findById(any())).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () ->
            service().delete(UUID.randomUUID(), UUID.randomUUID()));
    }

    // --- vote ---

    @Test
    void vote_whenNoExistingVote_thenCreatesVote() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(commentId);
        when(comment.getVoteCount()).thenReturn(0);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(voteRepository.findByIdCommentIdAndIdUserId(commentId, userId)).thenReturn(Optional.empty());
        when(commentRepository.save(comment)).thenReturn(comment);

        VoteResponse result = service().vote(commentId, userId, 1);

        verify(voteRepository).save(any(CommentVote.class));
        verify(comment).adjustVoteCount(1);
        assertEquals(1, result.userVote());
    }

    @Test
    void vote_whenSameVoteExists_thenTogglesOff() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(commentId);
        when(comment.getVoteCount()).thenReturn(5);
        CommentVote existing = new CommentVote(commentId, userId, (short) 1);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(voteRepository.findByIdCommentIdAndIdUserId(commentId, userId)).thenReturn(Optional.of(existing));
        when(commentRepository.save(comment)).thenReturn(comment);

        VoteResponse result = service().vote(commentId, userId, 1);

        assertNull(result.userVote());
        verify(voteRepository).delete(existing);
        verify(comment).adjustVoteCount(-1);
    }

    @Test
    void vote_whenOppositeVoteExists_thenFlipsVote() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(commentId);
        when(comment.getVoteCount()).thenReturn(3);
        CommentVote existing = new CommentVote(commentId, userId, (short) -1);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(voteRepository.findByIdCommentIdAndIdUserId(commentId, userId)).thenReturn(Optional.of(existing));
        when(commentRepository.save(comment)).thenReturn(comment);

        VoteResponse result = service().vote(commentId, userId, 1);

        assertEquals(1, result.userVote());
        verify(comment).adjustVoteCount(2); // +1 - (-1) = 2
    }

    @Test
    void vote_whenInvalidValue_thenThrowsValidation() {
        UUID commentId = UUID.randomUUID();
        Comment comment = mock(Comment.class);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThrows(ValidationException.class, () ->
            service().vote(commentId, UUID.randomUUID(), 0));
    }
}
