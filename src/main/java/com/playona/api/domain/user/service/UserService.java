package com.playona.api.domain.user.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.repository.PlatformRepository;
import com.playona.api.domain.user.dto.PlatformPreferenceRequest;
import com.playona.api.domain.user.dto.PlatformPreferenceResponse;
import com.playona.api.domain.user.dto.UpdateUserRequest;
import com.playona.api.domain.user.dto.UserResponse;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserPlatformPreference;
import com.playona.api.domain.user.repository.UserPlatformPreferenceRepository;
import com.playona.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PlatformRepository platformRepository;
    private final UserPlatformPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public UserResponse getMyInfoByUuid(String userUuid) {
        return toUserResponse(getUserByUuidOrThrow(userUuid));
    }

    public UserResponse updateMyInfoByUuid(String userUuid, UpdateUserRequest request) {
        User user = getUserByUuidOrThrow(userUuid);
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl());
        }
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<PlatformPreferenceResponse> getMyPlatformsByUuid(String userUuid) {
        return toPlatformPreferenceResponses(getUserByUuidOrThrow(userUuid));
    }

    public List<PlatformPreferenceResponse> updateMyPlatformsByUuid(String userUuid, List<PlatformPreferenceRequest> requests) {
        return updatePlatforms(getUserByUuidOrThrow(userUuid), requests);
    }

    private List<PlatformPreferenceResponse> updatePlatforms(User user, List<PlatformPreferenceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Platform preference list cannot be empty");
        }

        preferenceRepository.deleteByUser(user);

        requests.stream()
            .sorted(Comparator.comparingInt(PlatformPreferenceRequest::getPriority))
            .forEach(request -> {
                Platform platform = platformRepository.findById(request.getPlatformId())
                    .orElseThrow(() -> new IllegalArgumentException("Platform not found: " + request.getPlatformId()));
                preferenceRepository.save(UserPlatformPreference.builder()
                    .user(user)
                    .platform(platform)
                    .priority(request.getPriority())
                    .build());
            });

        return toPlatformPreferenceResponses(user);
    }

    private List<PlatformPreferenceResponse> toPlatformPreferenceResponses(User user) {
        return preferenceRepository.findByUserOrderByPriorityAsc(user)
            .stream()
            .map(pref -> PlatformPreferenceResponse.builder()
                .platformId(pref.getPlatform().getId())
                .platformName(pref.getPlatform().getName())
                .priority(pref.getPriority())
                .build())
            .toList();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
            .userUuid(user.getUserUuid())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImageUrl(user.getProfileImageUrl())
            .createdAt(user.getCreatedAt())
            .build();
    }

    private User getUserByUuidOrThrow(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userUuid));
    }
}