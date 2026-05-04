package com.playona.api.domain.link.controller;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.service.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkService linkService;

  @PostMapping
  public ResponseEntity<?> createLink(@RequestBody Map<String, String> body) {
    String url = body.get("url");
    SharedLink sharedLink = linkService.createLink(url);

    return ResponseEntity.ok(new LinkResponse(sharedLink));
  }
  @GetMapping("/{shortCode}")
  public ResponseEntity<?> getLink(@PathVariable String shortCode) {
    SharedLink sharedLink = linkService.getLink(shortCode);

    return ResponseEntity.ok(new LinkResponse(sharedLink));
  }
  @GetMapping("/{shortCode}/redirect")
  public ResponseEntity<?> redirect(@PathVariable String shortCode) {
    String url = linkService.getRedirectUrl(shortCode);
    return ResponseEntity.status(302)
        .header("Location", url)
        .build();
  }
}