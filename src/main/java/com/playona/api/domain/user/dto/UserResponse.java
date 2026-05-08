package com.playona.api.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
    private String userUuid;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
}