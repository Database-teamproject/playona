package com.playona.api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonPropertyOrder({
        "userUuid",
        "email",
        "nickname",
        "profileImageUrl",
        "createdAt"
})
public class UserResponse {
    private String userUuid;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
}
