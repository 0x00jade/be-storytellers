package com.demo.bestorytellers.search.service;

import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.story.dto.StoryCardResponse;
import com.demo.bestorytellers.story.service.StoryService;
import com.demo.bestorytellers.user.dto.UserCardResponse;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchService {

    private final StoryService storyService;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public SearchService(StoryService storyService, UserRepository userRepository,
                         FollowRepository followRepository) {
        this.storyService = storyService;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<StoryCardResponse> searchStories(String q, String tag, String status,
                                                         String lang, String sort, int page, int size,
                                                         UUID currentUserId) {
        if (q == null || q.trim().length() < 2) {
            throw new ValidationException("Query must be at least 2 characters");
        }
        return storyService.browse(tag, status, lang, sort, false, page, size, currentUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCardResponse> searchUsers(String q, UUID currentUserId, int page, int size) {
        if (q == null || q.trim().length() < 2) {
            throw new ValidationException("Query must be at least 2 characters");
        }
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return PageResponse.from(
            userRepository.searchByUsernameOrDisplayName(q.trim(), pageable)
                .map(u -> toUserCard(u, currentUserId)));
    }

    private UserCardResponse toUserCard(User u, UUID currentUserId) {
        boolean isFollowing = currentUserId != null
            && followRepository.existsByIdFollowerIdAndIdFollowingId(currentUserId, u.getId());
        long followers = followRepository.countByIdFollowingId(u.getId());
        return new UserCardResponse(
            u.getId(), u.getUsername(), u.getDisplayName(),
            u.getAvatarUrl(), u.getBio(), 0, followers, isFollowing);
    }
}
