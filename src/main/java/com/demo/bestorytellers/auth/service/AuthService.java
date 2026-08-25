package com.demo.bestorytellers.auth.service;

import com.demo.bestorytellers.auth.dto.TokenResponse;
import com.demo.bestorytellers.auth.security.JwtUtil;
import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.exception.UnauthorizedException;
import com.demo.bestorytellers.user.dto.UserResponse;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final HttpClient httpClient;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public AuthService(
        JwtUtil jwtUtil,
        RedisTemplate<String, String> redisTemplate,
        UserRepository userRepository,
        HttpClient httpClient,
        @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry,
        @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.httpClient = httpClient;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    private record GoogleUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name,
        String picture,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName
    ) {}

    @Transactional
    public TokenResponse verifyGoogleToken(String googleAccessToken) {
        GoogleUserInfo userInfo;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_USERINFO_URL))
                .header("Authorization", "Bearer " + googleAccessToken)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UnauthorizedException("Invalid Google access token");
            }
            userInfo = OBJECT_MAPPER.readValue(response.body(), GoogleUserInfo.class);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new UnauthorizedException("Failed to verify Google token");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnauthorizedException("Failed to verify Google token");
        }

        if (!Boolean.TRUE.equals(userInfo.emailVerified())) {
            throw new UnauthorizedException("Google email not verified");
        }

        String email = userInfo.email();
        String providerId = userInfo.sub();
        String displayName = userInfo.name();
        String avatarUrl = userInfo.picture();

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewUser(email, displayName, avatarUrl, providerId));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken();
        Duration ttl = Duration.ofSeconds(refreshTokenExpiry);

        redisTemplate.opsForValue().set("session:" + user.getId(), newRefreshToken, ttl);
        redisTemplate.opsForValue().set("rt:" + newRefreshToken, user.getId().toString(), ttl);

        return TokenResponse.of(accessToken, newRefreshToken, accessTokenExpiry);
    }

    private User createNewUser(String email, String displayName, String avatarUrl, String providerId) {
        String base = (displayName != null && !displayName.isBlank())
                ? displayName.toLowerCase().replaceAll("[^a-z0-9]", "")
                : "user";
        if (base.isEmpty()) base = "user";

        String username;
        do {
            username = base + (int) (Math.random() * 9000 + 1000);
        } while (userRepository.existsByUsername(username));

        return userRepository.save(new User(email, username, displayName, avatarUrl, "GOOGLE", providerId));
    }

    /**
     * Refresh access token using the provided refresh token.
     * Uses reverse lookup (rt:{refreshToken} → userId) to avoid requiring userId in the request.
     */
    @Transactional
    public TokenResponse refresh(String providedRefreshToken) {
        String userIdStr = redisTemplate.opsForValue().get("rt:" + providedRefreshToken);
        if (userIdStr == null) {
            throw new UnauthorizedException("Session expired");
        }
        UUID userId = UUID.fromString(userIdStr);

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
