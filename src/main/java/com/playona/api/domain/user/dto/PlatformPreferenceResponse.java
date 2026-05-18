package com.playona.api.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformPreferenceResponse {
    private Long platformId;
    private String platformName;
    private Integer priority;
}