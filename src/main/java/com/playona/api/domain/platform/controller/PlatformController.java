package com.playona.api.domain.platform.controller;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
public class PlatformController {

  private final PlatformRepository platformRepository;

  @GetMapping
  public ResponseEntity<List<Platform>> getPlatforms() {
    return ResponseEntity.ok(platformRepository.findByIsActiveTrue());
  }
}