package com.demo.bestorytellers.notification.service;

import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.notification.dto.MarkReadResponse;
import com.demo.bestorytellers.notification.dto.NotificationResponse;
import com.demo.bestorytellers.notification.dto.SingleReadResponse;
import com.demo.bestorytellers.notification.entity.Notification;
import com.demo.bestorytellers.notification.entity.NotificationType;
import com.demo.bestorytellers.notification.repository.NotificationRepository;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                StoryRepository storyRepository,
                                FollowRepository followRepository,
                                ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.storyRepository = storyRepository;
        this.followRepository = followRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void createNewChapterNotifications(UUID storyId, UUID chapterId, UUID authorId) {
        try {
            Story story = storyRepository.findById(storyId).orElse(null);
            if (story == null) return;

            List<UUID> followerIds = followRepository.findFollowingIds(authorId);
            Set<UUID> recipients = new HashSet<>(followerIds);
            recipients.remove(authorId);

            for (UUID recipientId : recipients) {
                User recipient = userRepository.findById(recipientId).orElse(null);
                if (recipient == null || !recipient.isActive()) continue;
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("storyId", storyId.toString());
                    payload.put("storyTitle", story.getTitle());
                    payload.put("storySlug", story.getSlug());
                    payload.put("chapterId", chapterId.toString());
                    payload.put("authorUsername", story.getAuthor().getUsername());
                    Notification n = new Notification(recipient, NotificationType.NEW_CHAPTER,
                        objectMapper.writeValueAsString(payload));
                    notificationRepository.save(n);
                    notificationRepository.purgeExcessUnread(recipientId);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize NEW_CHAPTER notification payload for recipient {}", recipientId, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create NEW_CHAPTER notifications for story {}", storyId, e);
        }
    }

    @Async
    @Transactional
    public void createNewFollowerNotification(UUID followedUserId, UUID followerUserId) {
        try {
            User target = userRepository.findById(followedUserId).orElse(null);
            User follower = userRepository.findById(followerUserId).orElse(null);
            if (target == null || follower == null) return;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("followerUserId", followerUserId.toString());
            payload.put("followerUsername", follower.getUsername());
            payload.put("followerAvatarUrl", follower.getAvatarUrl());

            Notification n = new Notification(target, NotificationType.NEW_FOLLOWER,
                objectMapper.writeValueAsString(payload));
            notificationRepository.save(n);
            notificationRepository.purgeExcessUnread(followedUserId);
        } catch (Exception e) {
            log.error("Failed to create NEW_FOLLOWER notification for user {}", followedUserId, e);
        }
    }

    @Async
    @Transactional
    public void createCommentReplyNotification(UUID recipientId, UUID commentId, String storySlug,
                                               int chapterNumber, String replierUsername, String preview) {
        try {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null) return;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("commentId", commentId.toString());
            payload.put("storySlug", storySlug);
            payload.put("chapterNumber", chapterNumber);
            payload.put("replierUsername", replierUsername);
            payload.put("preview", preview.length() > 100 ? preview.substring(0, 100) : preview);

            Notification n = new Notification(recipient, NotificationType.COMMENT_REPLY,
                objectMapper.writeValueAsString(payload));
            notificationRepository.save(n);
            notificationRepository.purgeExcessUnread(recipientId);
        } catch (Exception e) {
            log.error("Failed to create COMMENT_REPLY notification for user {}", recipientId, e);
        }
    }

    @Async
    @Transactional
    public void createStoryCompleteNotification(UUID storyId) {
        try {
            Story story = storyRepository.findById(storyId).orElse(null);
            if (story == null) return;

            List<UUID> followerIds = followRepository.findFollowingIds(story.getAuthor().getId());
            Set<UUID> recipients = new HashSet<>(followerIds);
            recipients.remove(story.getAuthor().getId());

            for (UUID recipientId : recipients) {
                User recipient = userRepository.findById(recipientId).orElse(null);
                if (recipient == null || !recipient.isActive()) continue;
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("storyId", storyId.toString());
                    payload.put("storyTitle", story.getTitle());
                    payload.put("storySlug", story.getSlug());
                    payload.put("authorUsername", story.getAuthor().getUsername());
                    Notification n = new Notification(recipient, NotificationType.STORY_COMPLETE,
                        objectMapper.writeValueAsString(payload));
                    notificationRepository.save(n);
                    notificationRepository.purgeExcessUnread(recipientId);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize STORY_COMPLETE notification payload for recipient {}", recipientId, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create STORY_COMPLETE notifications for story {}", storyId, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(UUID userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var notifications = unreadOnly
            ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(notifications.map(n ->
            new NotificationResponse(n.getId(), n.getType(), n.getPayload(), n.isRead(), n.getCreatedAt())));
    }

    @Transactional
    public MarkReadResponse markAllRead(UUID userId) {
        int count = notificationRepository.markAllRead(userId);
        return new MarkReadResponse(count);
    }

    @Transactional
    public SingleReadResponse markOneRead(UUID notificationId, UUID userId) {
        Notification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!n.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your notification");
        }
        n.markRead();
        notificationRepository.save(n);
        return new SingleReadResponse(n.getId(), true);
    }
}
