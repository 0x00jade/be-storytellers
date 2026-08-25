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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private HttpClient httpClient;
    @Mock private ValueOperations<String, String> valueOps;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jwtUtil, redisTemplate, userRepository, httpClient, 900L, 604800L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // --- verifyGoogleToken tests ---

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenValidToken_thenCreatesNewUserAndReturnsTokens() throws Exception {
        String googleAccessToken = "ya29.valid-access-token";
        String userInfoJson = """
            {"sub":"123","email":"new@gmail.com","email_verified":true,"name":"New User","picture":"https://pic.url"}
            """;

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(userInfoJson);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(any())).thenReturn(false);
        User savedUser = new User("new@gmail.com", "newuser1234", "New User", "https://pic.url", "GOOGLE", "123");
        ReflectionTestUtils.setField(savedUser, "id", UUID.randomUUID());
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");

        TokenResponse result = authService.verifyGoogleToken(googleAccessToken);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenExistingUser_thenReturnsTokensWithoutCreating() throws Exception {
        String userInfoJson = """
            {"sub":"456","email":"existing@gmail.com","email_verified":true,"name":"Existing","picture":null}
            """;
        User existing = new User("existing@gmail.com", "existinguser", "Existing", null, "GOOGLE", "456");
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(userInfoJson);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        when(userRepository.findByEmail("existing@gmail.com")).thenReturn(Optional.of(existing));
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");

        authService.verifyGoogleToken("ya29.token");

        verify(userRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenGoogleReturnsNon200_thenThrowsUnauthorized() throws Exception {
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(401);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> authService.verifyGoogleToken("ya29.bad-token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid Google access token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenEmailNotVerified_thenThrowsUnauthorized() throws Exception {
        String userInfoJson = """
            {"sub":"789","email":"unverified@gmail.com","email_verified":false,"name":"Unverified","picture":null}
            """;

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(userInfoJson);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> authService.verifyGoogleToken("ya29.token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Google email not verified");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenNetworkFails_thenThrowsUnauthorized() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
            .thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> authService.verifyGoogleToken("ya29.token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Failed to verify Google token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyGoogleToken_whenUserIsInactive_thenThrowsUnauthorized() throws Exception {
        String userInfoJson = """
            {"sub":"111","email":"banned@gmail.com","email_verified":true,"name":"Banned","picture":null}
            """;
        User bannedUser = new User("banned@gmail.com", "banneduser", "Banned", null, "GOOGLE", "111");
        bannedUser.setActive(false);

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(userInfoJson);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(userRepository.findByEmail("banned@gmail.com")).thenReturn(Optional.of(bannedUser));

        assertThatThrownBy(() -> authService.verifyGoogleToken("ya29.token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Account is inactive");
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
        Instant expiry = Instant.now().minusSeconds(60);

        when(valueOps.get("session:" + userId)).thenReturn(null);

        authService.logout(jti, userId, expiry);

        verify(valueOps, never()).set(eq("jwt:blacklist:" + jti), any(), any());
        verify(redisTemplate).delete("session:" + userId);
    }
}
