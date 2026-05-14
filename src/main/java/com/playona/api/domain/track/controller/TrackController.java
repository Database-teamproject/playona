package com.playona.api.domain.track.controller;

import com.playona.api.domain.track.service.TrackService;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<?>> resolveTrack(@RequestParam String url) {
        return ResponseEntity.ok(ApiResponse.ok(trackService.resolveTrack(url)));
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<ApiResponse<?>> getTrackDetail(@PathVariable Long trackId) {
        return ResponseEntity.ok(ApiResponse.ok(trackService.getTrackDetail(trackId)));
    }
}
