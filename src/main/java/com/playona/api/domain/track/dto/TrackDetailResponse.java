package com.playona.api.domain.track.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class TrackDetailResponse {
    private Long trackId;
    private String title;
    private String artist;
    private String album;
    private LocalDate releaseDate;
    private Integer durationMs;
    private String isrc;
    private String thumbnailUrl;
    private String sourceUrl;
}