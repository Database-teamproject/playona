package com.playona.api.domain.track.controller;

import com.playona.api.domain.track.dto.TrackDetailResponse;
import com.playona.api.domain.track.dto.TrackResolveResponse;
import com.playona.api.domain.track.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @GetMapping("/resolve")
    public TrackResolveResponse resolveTrack(@RequestParam String url) {
        return trackService.resolveTrack(url);
    }

    @GetMapping("/{trackId}")
    public TrackDetailResponse getTrackDetail(@PathVariable Long trackId) {
        return trackService.getTrackDetail(trackId);
    }
}