package com.playona.api.domain.link.service;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import com.playona.api.domain.track.service.SpotifyTrackService;
import com.playona.api.domain.track.service.TrackMatchingService;
import com.playona.api.domain.track.service.YoutubeTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkService {

  private final YoutubeTrackService youtubeTrackService;
  private final SpotifyTrackService spotifyTrackService;
  private final SharedLinkRepository sharedLinkRepository;
  private final TrackMatchingService trackMatchingService;

  @Transactional
  public SharedLink createLink(String url) {
    Track track = findOrCreateTrack(url);
    String shortCode = generateShortCode();
    SharedLink sharedLink = new SharedLink(shortCode, track);
    sharedLinkRepository.save(sharedLink);

    // 모든 플랫폼 매칭 (비동기적으로 처리)
    trackMatchingService.matchAll(track); 

    return sharedLink;
  }

  private Track findOrCreateTrack(String url) {
    if (url.contains("spotify.com")) {
      return spotifyTrackService.getTrackFromUrl(url);
    } else if (url.contains("youtube.com") || url.contains("youtu.be") || url.contains("music.youtube.com")) {
      return youtubeTrackService.getTrackFromUrl(url);
    }
    throw new IllegalArgumentException("지원하지 않는 플랫폼 URL입니다: " + url);
  }

  private String generateShortCode() {
    String code;
    do {
      code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    } while (sharedLinkRepository.existsByShortCode(code));
    return code;
  }
  public SharedLink getLink(String shortCode) {
    return sharedLinkRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));
  }
  public String getRedirectUrl(String shortCode) {
    SharedLink sharedLink = sharedLinkRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));

    return sharedLink.getTrack().getSourceUrl();
  }
}