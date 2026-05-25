package com.demo.bestorytellers.user.service;

import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.social.entity.Follow;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.user.dto.FollowResponse;
import com.demo.bestorytellers.user.dto.UpdateUserRequest;
import com.demo.bestorytellers.user.dto.UserCardResponse;
import com.demo.bestorytellers.user.dto.UserResponse;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public UserService(UserRepository userRepository, FollowRepository followRepository,
                       RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username, UUID currentUserId) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        long followers = followRepository.countByIdFollowingId(user.getId());
        long following = followRepository.countByIdFollowerId(user.getId());
        boolean isFollowing = currentUserId != null
            && followRepository.existsByIdFollowerIdAndIdFollowingId(currentUserId, user.getId());
        return toUserResponse(user, followers, following, isFollowing);
    }

    @Transactional
    public UserResponse updateProfile(UUID currentUserId, UpdateUserRequest request) {
        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new ConflictException("Username already taken: " + request.username());
            }
            redisTemplate.delete("user:" + user.getUsername());
            user.setUsername(request.username());
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }

        User saved = userRepository.save(user);
        long followers = followRepository.countByIdFollowingId(saved.getId());
        long following = followRepository.countByIdFollowerId(saved.getId());
        return toUserResponse(saved, followers, following, false);
    }

    @Transactional
    public FollowResponse follow(String username, UUID currentUserId) {
        User target = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (target.getId().equals(currentUserId)) {
            throw new ValidationException("Cannot follow yourself");
        }
        if (followRepository.existsByIdFollowerIdAndIdFollowingId(currentUserId, target.getId())) {
            throw new ConflictException("Already following: " + username);
        }
        followRepository.save(new Follow(currentUserId, target.getId()));
        redisTemplate.delete("feed:" + currentUserId);
        long count = followRepository.countByIdFollowingId(target.getId());
        return new FollowResponse(true, count);
    }

    @Transactional
    public FollowResponse unfollow(String username, UUID currentUserId) {
        User target = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        followRepository.deleteByIdFollowerIdAndIdFollowingId(currentUserId, target.getId());
        redisTemplate.delete("feed:" + currentUserId);
        long count = followRepository.countByIdFollowingId(target.getId());
        return new FollowResponse(false, count);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCardResponse> searchUsers(String q, int page, int size) {
        if (q == null || q.length() < 2) {
            throw new ValidationException("Query must be at least 2 characters");
        }
        var paged = userRepository.searchByUsernameOrDisplayName(
            q, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.from(paged.map(u -> new UserCardResponse(
            u.getId(), u.getUsername(), u.getDisplayName(),
            u.getAvatarUrl(), u.getBio(), 0, 0, false)));
    }

    private UserResponse toUserResponse(User user, long followers, long following, boolean isFollowing) {
        return new UserResponse(
            user.getId(), user.getEmail(), user.getUsername(),
            user.getDisplayName(), user.getAvatarUrl(), user.getBio(),
            followers, following, 0, isFollowing, user.getCreatedAt()
        );
    }
}
