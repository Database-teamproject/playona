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
    public UserResponse getMyInfo(Long userId) {
        User user = getUserOrThrow(userId);

        return UserResponse.builder()
                .userUuid(user.getUserUuid())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public UserResponse updateMyInfo(Long userId, UpdateUserRequest request) {
        User user = getUserOrThrow(userId);

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }

        if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl());
        }

        return UserResponse.builder()
                .userUuid(user.getUserUuid())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlatformPreferenceResponse> getMyPlatforms(Long userId) {
        User user = getUserOrThrow(userId);

        return preferenceRepository.findByUserOrderByPriorityAsc(user)
                .stream()
                .map(pref -> PlatformPreferenceResponse.builder()
                        .platformId(pref.getPlatform().getId())
                        .platformName(pref.getPlatform().getName())
                        .priority(pref.getPriority())
                        .build())
                .toList();
    }

    public List<PlatformPreferenceResponse> updateMyPlatforms(Long userId, List<PlatformPreferenceRequest> requests) {
        User user = getUserOrThrow(userId);

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Platform preference list cannot be empty");
        }

        List<PlatformPreferenceRequest> sortedRequests = requests.stream()
                .sorted(Comparator.comparingInt(PlatformPreferenceRequest::getPriority))
                .toList();

        preferenceRepository.deleteByUser(user);

        for (PlatformPreferenceRequest request : sortedRequests) {
            Platform platform = platformRepository.findById(request.getPlatformId())
                    .orElseThrow(() -> new IllegalArgumentException("Platform not found: " + request.getPlatformId()));

            UserPlatformPreference preference = UserPlatformPreference.builder()
                    .user(user)
                    .platform(platform)
                    .priority(request.getPriority())
                    .build();

            preferenceRepository.save(preference);
        }

        return getMyPlatforms(userId);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}