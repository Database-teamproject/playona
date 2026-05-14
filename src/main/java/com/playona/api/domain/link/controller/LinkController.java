package com.playona.api.domain.link.controller;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.service.LinkService;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

  @Value("${app.base-url}")
  private String baseUrl;
  private final LinkService linkService;
  private final PlatformTrackRepository platformTrackRepository;

  @PostMapping
  public ResponseEntity<ApiResponse<LinkResponse>> createLink(@RequestBody Map<String, String> body) {
    String url = body.get("url");
    return ResponseEntity.ok(ApiResponse.ok(linkService.createLink(url)));
  }

  @GetMapping("/my")
  public ResponseEntity<ApiResponse<?>> getMyLinks(@AuthenticationPrincipal String userUuid) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getMyLinks(userUuid)));
  }

  @GetMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<?>> getLink(@PathVariable String shortCode) {
    SharedLink sharedLink = linkService.getLink(shortCode);
    List<PlatformTrack> platformTracks = platformTrackRepository.findByTrack(sharedLink.getTrack());
    return ResponseEntity.ok(ApiResponse.ok(new LinkResponse(sharedLink, baseUrl, platformTracks)));
  }

  @GetMapping("/{shortCode}/platforms")
  public ResponseEntity<ApiResponse<?>> getPlatformUrls(@PathVariable String shortCode) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getPlatformUrls(shortCode)));
  }
  @GetMapping("/{shortCode}/redirect")
  public ResponseEntity<?> redirect(@PathVariable String shortCode,
                                    @AuthenticationPrincipal String userUuid) {
    String url = linkService  .getRedirectUrl(shortCode, userUuid);
    return ResponseEntity.status(302)
            .header("Location", url)
            .build();
  }


}