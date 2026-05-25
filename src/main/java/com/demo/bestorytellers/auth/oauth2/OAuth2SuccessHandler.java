package com.demo.bestorytellers.auth.oauth2;

import com.demo.bestorytellers.auth.security.JwtUtil;
import com.demo.bestorytellers.auth.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final String frontendUrl;
    private final long refreshTokenExpiry;

    public OAuth2SuccessHandler(
        JwtUtil jwtUtil,
        RedisTemplate<String, String> redisTemplate,
        @Value("${app.frontend-url}") String frontendUrl,
        @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.frontendUrl = frontendUrl;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication auth) throws IOException {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(principal.getUserId(), principal.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken();
        Duration ttl = Duration.ofSeconds(refreshTokenExpiry);

        // Forward mapping: userId → refreshToken (session store)
        redisTemplate.opsForValue().set("session:" + principal.getUserId(), refreshToken, ttl);
        // Reverse mapping: refreshToken → userId (for token refresh without userId in request)
        redisTemplate.opsForValue().set("rt:" + refreshToken, principal.getUserId().toString(), ttl);

        String redirectUrl = frontendUrl + "/auth/callback#access=" + accessToken + "&refresh=" + refreshToken;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
