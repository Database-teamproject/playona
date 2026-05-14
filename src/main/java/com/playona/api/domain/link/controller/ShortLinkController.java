package com.playona.api.domain.link.controller;

import com.playona.api.domain.link.service.LinkService;
import com.playona.api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/t")
@RequiredArgsConstructor
public class ShortLinkController {

  private final LinkService linkService;

  @GetMapping("/{shortCode}")
  public ResponseEntity<?> redirect(@PathVariable String shortCode,
      @AuthenticationPrincipal String userUuid) {
    if (userUuid == null) {
      linkService.incrementClickCount(shortCode);
      return ResponseEntity.ok(ApiResponse.ok(linkService.getPlatformUrls(shortCode)));
    }
    String url = linkService.getRedirectUrl(shortCode, userUuid);
    return ResponseEntity.status(302)
        .header("Location", url)
        .build();
  }
}
