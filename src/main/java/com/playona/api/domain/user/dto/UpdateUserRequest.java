package com.playona.api.domain.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
    private String nickname;
    private String profileImageUrl;
}