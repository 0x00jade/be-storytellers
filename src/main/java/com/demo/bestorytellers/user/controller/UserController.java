package com.demo.bestorytellers.user.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.user.dto.AvatarResponse;
import com.demo.bestorytellers.user.dto.FollowResponse;
import com.demo.bestorytellers.user.dto.UpdateUserRequest;
import com.demo.bestorytellers.user.dto.UserResponse;
import com.demo.bestorytellers.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(userService.getByUsername(principal.getUsername(), currentUserId)));
    }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
        @PathVariable String username,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(userService.getByUsername(username, currentUserId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            userService.updateProfile(principal.getUserId(), request)));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse<AvatarResponse>> uploadAvatar(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            userService.uploadAvatar(principal.getUserId(), file)));
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<ApiResponse<FollowResponse>> follow(
        @PathVariable String username,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            userService.follow(username, principal.getUserId())));
    }

    @DeleteMapping("/{username}/follow")
    public ResponseEntity<ApiResponse<FollowResponse>> unfollow(
        @PathVariable String username,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            userService.unfollow(username, principal.getUserId())));
    }
}
