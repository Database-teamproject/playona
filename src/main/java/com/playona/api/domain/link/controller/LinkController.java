package com.playona.api.domain.link.controller;

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

    return ResponseEntity.ok(Map.of(
        "shortCode", sharedLink.getShortCode(),
        "trackTitle", sharedLink.getTrack().getTitle(),
        "trackArtist", sharedLink.getTrack().getArtist(),
        "thumbnailUrl", sharedLink.getTrack().getThumbnailUrl() != null
            ? sharedLink.getTrack().getThumbnailUrl() : ""
    ));
  }

}