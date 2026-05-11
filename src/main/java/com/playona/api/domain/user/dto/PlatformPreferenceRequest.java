package com.playona.api.domain.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformPreferenceRequest {
    private Integer platformId;
    private Integer priority;
}