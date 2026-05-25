package com.demo.bestorytellers.social.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.notification.service.NotificationService;
import com.demo.bestorytellers.social.dto.CommentResponse;
import com.demo.bestorytellers.social.dto.CommentUserDto;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository voteRepository;
    private final ChapterRepository chapterRepository;
    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CommentService(CommentRepository commentRepository, CommentVoteRepository voteRepository,
                          ChapterRepository chapterRepository, StoryRepository storyRepository,
                          UserRepository userRepository, NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.chapterRepository = chapterRepository;
        this.storyRepository = storyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listTopLevel(String slug, int chapterNumber, String sort,
                                                      UUID currentUserId, int page, int size) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(story.getId(), chapterNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterNumber));

        Sort s = "top".equals(sort)
            ? Sort.by("voteCount").descending()
            : Sort.by("createdAt").descending();
        var pageable = PageRequest.of(page, Math.min(size, 100), s);
        return PageResponse.from(
            commentRepository.findTopLevelByChapterId(chapter.getId(), pageable)
                .map(c -> toResponse(c, currentUserId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listReplies(UUID commentId, UUID currentUserId, int page, int size) {
        commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return PageResponse.from(
            commentRepository.findByParentIdOrderByCreatedAtAsc(commentId, pageable)
                .map(c -> toResponse(c, currentUserId)));
    }

    @Transactional
    public CommentResponse create(String slug, int chapterNumber, UUID userId, CreateCommentRequest request) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(story.getId(), chapterNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterNumber));

        if (chapter.getStatus() != ChapterStatus.PUBLISHED) {
            throw new ForbiddenException("Chapter is not published");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + request.parentId()));
            if (parent.getParent() != null) {
                throw new ValidationException("Cannot reply to a reply");
            }
        }

        Comment comment = new Comment(chapter, user, parent, request.content());
        Comment saved = commentRepository.save(comment);

        if (parent != null && !parent.getUser().getId().equals(userId)) {
            notificationService.createCommentReplyNotification(
                parent.getUser().getId(), saved.getId(),
                story.getSlug(), chapterNumber,
                user.getUsername(), saved.getContent());
        }

        return toResponse(saved, userId);
    }

    @Transactional
    public CommentResponse update(UUID commentId, UUID userId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this comment");
        }
        comment.setContent(request.content());
        return toResponse(commentRepository.save(comment), userId);
    }

    @Transactional
    public void delete(UUID commentId, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this comment");
        }

        long replyCount = commentRepository.countReplies(commentId);
        if (replyCount > 0) {
            comment.setDeleted(true);
            comment.setContent("[deleted]");
            commentRepository.save(comment);
        } else {
            commentRepository.delete(comment);
        }
    }

    @Transactional
    public VoteResponse vote(UUID commentId, UUID userId, int voteValue) {
        if (voteValue != 1 && voteValue != -1) {
            throw new ValidationException("Vote must be 1 or -1");
        }

        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        var existing = voteRepository.findByIdCommentIdAndIdUserId(commentId, userId);

        if (existing.isPresent()) {
            CommentVote existingVote = existing.get();
            if (existingVote.getVote() == voteValue) {
                // Toggle off
                comment.adjustVoteCount(-voteValue);
                voteRepository.delete(existingVote);
                commentRepository.save(comment);
                return new VoteResponse(comment.getVoteCount(), null);
            } else {
                // Flip vote
                int delta = voteValue - existingVote.getVote();
                comment.adjustVoteCount(delta);
                existingVote.setVote((short) voteValue);
                voteRepository.save(existingVote);
                commentRepository.save(comment);
                return new VoteResponse(comment.getVoteCount(), voteValue);
            }
        } else {
            // New vote
            comment.adjustVoteCount(voteValue);
            voteRepository.save(new CommentVote(commentId, userId, (short) voteValue));
            commentRepository.save(comment);
            return new VoteResponse(comment.getVoteCount(), voteValue);
        }
    }

    private CommentResponse toResponse(Comment c, UUID currentUserId) {
        var userDto = new CommentUserDto(
            c.getUser().getId(), c.getUser().getUsername(),
            c.getUser().getDisplayName(), c.getUser().getAvatarUrl());
        Integer userVote = currentUserId != null
            ? voteRepository.findByIdCommentIdAndIdUserId(c.getId(), currentUserId)
                .map(v -> (int) v.getVote())
                .orElse(null)
            : null;
        long replyCount = commentRepository.countReplies(c.getId());
        return new CommentResponse(c.getId(), userDto, c.getContent(), c.getVoteCount(),
            userVote, replyCount, c.isDeleted(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
