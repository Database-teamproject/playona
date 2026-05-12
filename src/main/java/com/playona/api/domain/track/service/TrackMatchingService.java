package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformRepository;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.domain.track.entity.Track;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackMatchingService {

  private final PlatformRepository platformRepository;
  private final PlatformTrackRepository platformTrackRepository;
  private final SpotifyTrackService spotifyTrackService;
  private final YoutubeTrackService youtubeTrackService;
  private final AppleTrackService appleTrackService;

  public List<PlatformTrack> matchAll(Track track) {
    List<Platform> platforms = platformRepository.findByIsActiveTrue();

    for (Platform platform : platforms) {
      // 이미 매칭된 플랫폼은 스킵
      if (platformTrackRepository.findByTrackAndPlatform(track, platform).isPresent()) {
        continue;
      }

      try {
        PlatformTrack platformTrack = matchToPlatform(track, platform);
        if (platformTrack != null) {
          platformTrackRepository.save(platformTrack);
        }
      } catch (Exception e) {
        log.warn("플랫폼 매칭 실패 - platform: {}, track: {}, error: {}",
            platform.getSlug(), track.getTitle(), e.getMessage());
      }
    }

    return platformTrackRepository.findByTrack(track);
  }

  private PlatformTrack matchToPlatform(Track track, Platform platform) {
    return switch (platform.getSlug()) {
      case "spotify" -> spotifyTrackService.searchTrack(track, platform);
      case "ytmusic" -> youtubeTrackService.searchTrack(track, platform);
      case "apple" -> appleTrackService.searchTrack(track, platform);
      default -> null;
    };
  }
}