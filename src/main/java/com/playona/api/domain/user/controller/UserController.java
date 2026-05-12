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
        @AuthenticationPrincipal String userUuid
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyInfoByUuid(userUuid)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateMyInfo(
        @AuthenticationPrincipal String userUuid,
        @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyInfoByUuid(userUuid, request)));
    }

    @GetMapping("/platforms")
    public ResponseEntity<ApiResponse<List<PlatformPreferenceResponse>>> getMyPlatforms(
        @AuthenticationPrincipal String userUuid
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyPlatformsByUuid(userUuid)));
    }

    @PutMapping("/platforms")
    public ResponseEntity<ApiResponse<List<PlatformPreferenceResponse>>> updateMyPlatforms(
        @AuthenticationPrincipal String userUuid,
        @RequestBody List<PlatformPreferenceRequest> requests
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyPlatformsByUuid(userUuid, requests)));
    }
}