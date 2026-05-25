package com.demo.bestorytellers.auth.service;

import com.demo.bestorytellers.auth.dto.TokenResponse;
import com.demo.bestorytellers.auth.security.JwtUtil;
import com.demo.bestorytellers.common.exception.UnauthorizedException;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private ValueOperations<String, String> valueOps;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jwtUtil, redisTemplate, userRepository, 900L, 604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // --- refresh tests ---

    @Test
    void refresh_whenValidToken_thenReturnsNewTokens() {
        UUID userId = UUID.randomUUID();
        String refreshToken = UUID.randomUUID().toString();
        User user = new User("test@test.com", "testuser", "Test", null, "GOOGLE", "google123");

        when(valueOps.get("rt:" + refreshToken)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn(refreshToken);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("new-refresh-token");

        TokenResponse result = authService.refresh(refreshToken);

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    void refresh_whenRefreshTokenNotInRedis_thenThrowsUnauthorized() {
        String refreshToken = UUID.randomUUID().toString();
        when(valueOps.get("rt:" + refreshToken)).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Session expired");
    }

    @Test
    void refresh_whenSessionMismatch_thenThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();
        String refreshToken = UUID.randomUUID().toString();

        when(valueOps.get("rt:" + refreshToken)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn("different-token");

        assertThatThrownBy(() -> authService.refresh(refreshToken))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void refresh_whenUserInactive_thenThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();
        String refreshToken = UUID.randomUUID().toString();
        User user = new User("test@test.com", "testuser", "Test", null, "GOOGLE", "google123");
        user.setActive(false);

        when(valueOps.get("rt:" + refreshToken)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn(refreshToken);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(refreshToken))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found or inactive");
    }

    @Test
    void refresh_whenTokensRotated_thenOldTokensDeleted() {
        UUID userId = UUID.randomUUID();
        String refreshToken = UUID.randomUUID().toString();
        User user = new User("test@test.com", "testuser", "Test", null, "GOOGLE", "google123");

        when(valueOps.get("rt:" + refreshToken)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn(refreshToken);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken()).thenReturn("new-rt");

        authService.refresh(refreshToken);

        verify(redisTemplate).delete("session:" + userId);
        verify(redisTemplate).delete("rt:" + refreshToken);
        verify(valueOps).set(eq("session:" + userId), eq("new-rt"), any());
        verify(valueOps).set(eq("rt:" + "new-rt"), eq(userId.toString()), any());
    }

    // --- logout tests ---

    @Test
    void logout_whenCalled_thenBlacklistsJtiAndDeletesSession() {
        String jti = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(300);

        when(valueOps.get("session:" + userId)).thenReturn(null);

        authService.logout(jti, userId, expiry);

        verify(valueOps).set(eq("jwt:blacklist:" + jti), eq("true"), any());
        verify(redisTemplate).delete("session:" + userId);
    }

    @Test
    void logout_whenRefreshTokenExists_thenAlsoDeletesReverseMapping() {
        String jti = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        String refreshToken = "some-refresh-token";
        Instant expiry = Instant.now().plusSeconds(300);

        when(valueOps.get("session:" + userId)).thenReturn(refreshToken);

        authService.logout(jti, userId, expiry);

        verify(redisTemplate).delete("rt:" + refreshToken);
        verify(redisTemplate).delete("session:" + userId);
    }

    @Test
    void logout_whenTokenAlreadyExpired_thenSkipsBlacklist() {
        String jti = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        Instant expiry = Instant.now().minusSeconds(60); // already expired

        when(valueOps.get("session:" + userId)).thenReturn(null);

        authService.logout(jti, userId, expiry);

        verify(valueOps, never()).set(eq("jwt:blacklist:" + jti), any(), any());
        verify(redisTemplate).delete("session:" + userId);
    }
}
