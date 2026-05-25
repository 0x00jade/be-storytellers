package com.demo.bestorytellers.auth.service;

import com.demo.bestorytellers.auth.dto.TokenResponse;
import com.demo.bestorytellers.auth.security.JwtUtil;
import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.exception.UnauthorizedException;
import com.demo.bestorytellers.user.dto.UserResponse;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public AuthService(
        JwtUtil jwtUtil,
        RedisTemplate<String, String> redisTemplate,
        UserRepository userRepository,
        @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry,
        @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    /**
     * Refresh access token using the provided refresh token.
     * Uses reverse lookup (rt:{refreshToken} → userId) to avoid requiring userId in the request.
     */
    @Transactional
    public TokenResponse refresh(String providedRefreshToken) {
        // Reverse lookup: get userId from refresh token
        String userIdStr = redisTemplate.opsForValue().get("rt:" + providedRefreshToken);
        if (userIdStr == null) {
            throw new UnauthorizedException("Session expired");
        }
        UUID userId = UUID.fromString(userIdStr);

        // Verify forward mapping matches
        String stored = redisTemplate.opsForValue().get("session:" + userId);
        if (!providedRefreshToken.equals(stored)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = userRepository.findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> new UnauthorizedException("User not found or inactive"));

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken();
        Duration ttl = Duration.ofSeconds(refreshTokenExpiry);

        // Rotate: delete old tokens, store new ones
        redisTemplate.delete("session:" + userId);
        redisTemplate.delete("rt:" + providedRefreshToken);
        redisTemplate.opsForValue().set("session:" + userId, newRefreshToken, ttl);
        redisTemplate.opsForValue().set("rt:" + newRefreshToken, userId.toString(), ttl);

        return TokenResponse.of(newAccessToken, newRefreshToken, accessTokenExpiry);
    }

    /**
     * Logout: blacklist the JWT jti and invalidate the session.
     */
    public void logout(String jti, UUID userId, Instant tokenExpiry) {
        long remainingTtl = tokenExpiry.getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingTtl > 0) {
            redisTemplate.opsForValue().set(
                "jwt:blacklist:" + jti, "true", Duration.ofSeconds(remainingTtl));
        }
        // Also clean up the refresh token reverse mapping if session exists
        String refreshToken = redisTemplate.opsForValue().get("session:" + userId);
        if (refreshToken != null) {
            redisTemplate.delete("rt:" + refreshToken);
        }
        redisTemplate.delete("session:" + userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(UserPrincipal principal) {
        User user = userRepository.findById(principal.getUserId())
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        return new UserResponse(
            user.getId(), user.getEmail(), user.getUsername(),
            user.getDisplayName(), user.getAvatarUrl(), user.getBio(),
            0, 0, 0, false, user.getCreatedAt()
        );
    }
}
