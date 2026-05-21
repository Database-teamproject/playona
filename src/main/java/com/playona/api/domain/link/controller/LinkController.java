package com.playona.api.domain.link.controller;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.service.LinkService;
import com.playona.api.global.common.ApiResponse;
import com.playona.api.global.config.LinkRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
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
  private final LinkRateLimiter rateLimiter;

  @PostMapping
  public ResponseEntity<ApiResponse<LinkResponse>> createLink(
      @RequestBody Map<String, String> body,
      HttpServletRequest request) {
    String ip = getClientIp(request);
    if (!rateLimiter.tryAcquire(ip)) {
      return ResponseEntity.status(429).body(ApiResponse.fail("요청이 너무 많습니다. 1분 후 다시 시도해주세요."));
    }
    String url = body.get("url");
    if (url == null || url.isBlank()) {
      return ResponseEntity.badRequest().body(ApiResponse.fail("url은 필수입니다."));
    }
    return ResponseEntity.ok(ApiResponse.ok(linkService.createLink(url)));
  }

  private String getClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
  }

  @GetMapping("/my")
  public ResponseEntity<ApiResponse<?>> getMyLinks(@AuthenticationPrincipal String userUuid) {
    return ResponseEntity.ok(ApiResponse.ok(linkService.getMyLinks(userUuid)));
  }

  @DeleteMapping("/{shortCode}")
  public ResponseEntity<ApiResponse<?>> deleteLink(
      @PathVariable String shortCode,
      @AuthenticationPrincipal String userUuid
  ) {
    if (userUuid == null) {
      return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
    }
    linkService.deleteLink(shortCode, userUuid);
    return ResponseEntity.ok(ApiResponse.ok(null));
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
