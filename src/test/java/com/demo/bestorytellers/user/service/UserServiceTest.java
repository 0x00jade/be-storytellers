package com.demo.bestorytellers.user.service;

import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.common.util.S3Util;
import com.demo.bestorytellers.social.entity.Follow;
import com.demo.bestorytellers.social.repository.FollowRepository;
import com.demo.bestorytellers.user.dto.FollowResponse;
import com.demo.bestorytellers.user.dto.UpdateUserRequest;
import com.demo.bestorytellers.user.dto.UserResponse;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private S3Util s3Util;

    private UserService userService;

    private final UUID userId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private User user;
    private User targetUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, followRepository, redisTemplate, s3Util);

        user = new User("user@example.com", "currentuser", "Current User", null, "GOOGLE", "sub1");
        ReflectionTestUtils.setField(user, "id", userId);

        targetUser = new User("target@example.com", "targetuser", "Target User", null, "GOOGLE", "sub2");
        ReflectionTestUtils.setField(targetUser, "id", targetId);
    }

    @Test
    void getByUsername_found_returnsResponse() {
        given(userRepository.findByUsername("targetuser")).willReturn(Optional.of(targetUser));
        given(followRepository.countByIdFollowingId(targetId)).willReturn(5L);
        given(followRepository.countByIdFollowerId(targetId)).willReturn(3L);
        given(followRepository.existsByIdFollowerIdAndIdFollowingId(userId, targetId)).willReturn(false);

        UserResponse result = userService.getByUsername("targetuser", userId);

        assertThat(result.username()).isEqualTo("targetuser");
        assertThat(result.followerCount()).isEqualTo(5L);
        assertThat(result.followingCount()).isEqualTo(3L);
        assertThat(result.isFollowing()).isFalse();
    }

    @Test
    void getByUsername_notFound_throwsException() {
        given(userRepository.findByUsername("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByUsername("unknown", userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not found: unknown");
    }

    @Test
    void updateProfile_changesUsernameSuccessfully() {
        UpdateUserRequest request = new UpdateUserRequest("newusername", "New Name", null);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByUsername("newusername")).willReturn(false);
        given(userRepository.save(any(User.class))).willReturn(user);
        given(followRepository.countByIdFollowingId(userId)).willReturn(0L);
        given(followRepository.countByIdFollowerId(userId)).willReturn(0L);

        UserResponse result = userService.updateProfile(userId, request);

        assertThat(result).isNotNull();
        then(redisTemplate).should().delete("user:" + "currentuser");
    }

    @Test
    void updateProfile_duplicateUsername_throwsConflict() {
        UpdateUserRequest request = new UpdateUserRequest("takenname", null, null);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByUsername("takenname")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Username already taken");
    }

    @Test
    void follow_success() {
        given(userRepository.findByUsername("targetuser")).willReturn(Optional.of(targetUser));
        given(followRepository.existsByIdFollowerIdAndIdFollowingId(userId, targetId)).willReturn(false);
        given(followRepository.save(any(Follow.class))).willReturn(new Follow(userId, targetId));
        given(followRepository.countByIdFollowingId(targetId)).willReturn(6L);

        FollowResponse result = userService.follow("targetuser", userId);

        assertThat(result.following()).isTrue();
        assertThat(result.followerCount()).isEqualTo(6L);
        then(redisTemplate).should().delete("feed:" + userId);
    }

    @Test
    void follow_selfFollow_throwsValidation() {
        given(userRepository.findByUsername("currentuser")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.follow("currentuser", userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Cannot follow yourself");
    }

    @Test
    void follow_alreadyFollowing_throwsConflict() {
        given(userRepository.findByUsername("targetuser")).willReturn(Optional.of(targetUser));
        given(followRepository.existsByIdFollowerIdAndIdFollowingId(userId, targetId)).willReturn(true);

        assertThatThrownBy(() -> userService.follow("targetuser", userId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Already following");
    }

    @Test
    void unfollow_success() {
        given(userRepository.findByUsername("targetuser")).willReturn(Optional.of(targetUser));
        given(followRepository.countByIdFollowingId(targetId)).willReturn(4L);

        FollowResponse result = userService.unfollow("targetuser", userId);

        assertThat(result.following()).isFalse();
        assertThat(result.followerCount()).isEqualTo(4L);
        then(followRepository).should().deleteByIdFollowerIdAndIdFollowingId(userId, targetId);
        then(redisTemplate).should().delete("feed:" + userId);
    }
}
