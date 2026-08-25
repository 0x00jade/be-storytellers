package com.demo.bestorytellers.auth.controller;

import com.demo.bestorytellers.auth.dto.RefreshTokenRequest;
import com.demo.bestorytellers.auth.dto.TokenResponse;
import com.demo.bestorytellers.auth.dto.VerifyGoogleToken;
import com.demo.bestorytellers.auth.security.JwtUtil;
import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.auth.service.AuthService;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.common.exception.UnauthorizedException;
import com.demo.bestorytellers.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/verify-token")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyGoogleToken(
            @Valid @RequestBody VerifyGoogleToken request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.verifyGoogleToken(request.accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
        @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestHeader("Authorization") String authHeader
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.extractJti(token);
        authService.logout(jti, principal.getUserId(), jwtUtil.extractExpiry(token));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.getMe(principal)));
    }
}
