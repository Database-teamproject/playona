package com.playona.api.domain.link.service;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import com.playona.api.domain.track.service.YoutubeTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkService {

  private final YoutubeTrackService youtubeTrackService;
  private final SharedLinkRepository sharedLinkRepository;

  @Transactional
  public SharedLink createLink(String url) {

    // 1. URL에서 플랫폼 식별 (지금은 임시로 제목/아티스트 하드코딩)
    Track track = findOrCreateTrack(url);

    // 2. short_code 생성
    String shortCode = generateShortCode();

    // 3. SharedLink 저장
    SharedLink sharedLink = new SharedLink(shortCode, track);
    return sharedLinkRepository.save(sharedLink);
  }

  private Track findOrCreateTrack(String url) {
    return youtubeTrackService.getTrackFromUrl(url);
  }

  private String generateShortCode() {
    String code;
    do {
      code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    } while (sharedLinkRepository.existsByShortCode(code));
    return code;
  }
  
}