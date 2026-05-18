package com.playona.api.domain.link.controller;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.service.LinkService;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkService linkService;

  @PostMapping
  public ResponseEntity<ApiResponse<LinkResponse>> createLink(@RequestBody Map<String, String> body) {
    String url = body.get("url");
    if (url == null || url.isBlank()) {
      return ResponseEntity.badRequest().body(ApiResponse.fail("url은 필수입니다."));
    }
    return ResponseEntity.ok(ApiResponse.ok(linkService.createLink(url)));
  }

  @GetMapping("/my")
  public ResponseEntity<ApiResponse<?>> getMyLinks(@AuthenticationPrincipal String userUuid) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getMyLinks(userUuid)));
  }

  @GetMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<?>> getLink(@PathVariable String shortCode) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getLinkResponse(shortCode)));
  }

  @GetMapping("/{shortCode}/platforms")
  public ResponseEntity<ApiResponse<?>> getPlatformUrls(@PathVariable String shortCode) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getPlatformUrls(shortCode)));
  }

  // 302 대신 JSON으로 URL 반환 — SPA에서 fetch()는 redirect를 자동으로 따라가므로
  // 목적지 URL을 알 수 없게 됨. 프론트가 받아서 window.location.href로 이동.
  @GetMapping("/{shortCode}/redirect")
  public ResponseEntity<ApiResponse<?>> redirect(@PathVariable String shortCode,
      @AuthenticationPrincipal String userUuid) {
    if (userUuid == null) {
      linkService.incrementClickCount(shortCode);
      return ResponseEntity.ok(ApiResponse.ok(linkService.getPlatformUrls(shortCode)));
    }
    String url = linkService.getRedirectUrl(shortCode, userUuid);
    return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
  }
}
