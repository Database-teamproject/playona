package com.playona.api.domain.user.controller;

import com.playona.api.domain.user.dto.PlatformPreferenceRequest;
import com.playona.api.domain.user.dto.PlatformPreferenceResponse;
import com.playona.api.domain.user.dto.UpdateUserRequest;
import com.playona.api.domain.user.dto.UserResponse;
import com.playona.api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse getMyInfo(
            @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId
    ) {
        return userService.getMyInfo(userId);
    }

    @PutMapping
    public UserResponse updateMyInfo(
            @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId,
            @RequestBody UpdateUserRequest request
    ) {
        return userService.updateMyInfo(userId, request);
    }

    @GetMapping("/platforms")
    public List<PlatformPreferenceResponse> getMyPlatforms(
            @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId
    ) {
        return userService.getMyPlatforms(userId);
    }

    @PutMapping("/platforms")
    public List<PlatformPreferenceResponse> updateMyPlatforms(
            @RequestHeader(value = "X-USER-ID", defaultValue = "1") Long userId,
            @RequestBody List<PlatformPreferenceRequest> requests
    ) {
        return userService.updateMyPlatforms(userId, requests);
    }
}