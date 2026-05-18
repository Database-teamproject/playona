package com.playona.api.domain.track.controller;

import com.playona.api.domain.track.service.TrackService;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    // 쓰기 작업이므로 POST로 변경 (GET은 idempotent해야 함)
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<?>> resolveTrack(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("url은 필수입니다."));
        }
        return ResponseEntity.ok(ApiResponse.ok(trackService.resolveTrack(url)));
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<ApiResponse<?>> getTrackDetail(@PathVariable Long trackId) {
        return ResponseEntity.ok(ApiResponse.ok(trackService.getTrackDetail(trackId)));
    }
}
