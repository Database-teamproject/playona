package com.playona.api.domain.user.controller;

import com.playona.api.domain.user.dto.PlatformPreferenceRequest;
import com.playona.api.domain.user.dto.PlatformPreferenceResponse;
import com.playona.api.domain.user.dto.UpdateUserRequest;
import com.playona.api.domain.user.dto.UserResponse;
import com.playona.api.domain.user.service.UserService;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
        @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyInfo(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateMyInfo(
        @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId,
        @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyInfo(userId, request)));
    }

    @GetMapping("/platforms")
    public ResponseEntity<ApiResponse<List<PlatformPreferenceResponse>>> getMyPlatforms(
        @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyPlatforms(userId)));
    }

    @PutMapping("/platforms")
    public ResponseEntity<ApiResponse<List<PlatformPreferenceResponse>>> updateMyPlatforms(
        @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId,
        @RequestBody List<PlatformPreferenceRequest> requests
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyPlatforms(userId, requests)));
    }
}