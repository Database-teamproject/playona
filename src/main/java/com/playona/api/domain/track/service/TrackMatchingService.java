package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformRepository;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.domain.track.entity.Track;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final MelonTrackService melonTrackService;
    private final FloTrackService floTrackService;
    private final GenieTrackService genieTrackService;

    // REQUIRES_NEW: 호출자(createLink)의 트랜잭션과 독립적으로 실행
    // 플랫폼 매칭 실패 시 createLink 전체가 롤백되는 것을 방지
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PlatformTrack> matchAll(Track track) {
        return match(track);
    }

    // 기존 platform_tracks를 삭제한 직후에는 같은 트랜잭션에서 다시 생성해야 한다.
    @Transactional
    public List<PlatformTrack> rematchAll(Track track) {
        return match(track);
    }

    private List<PlatformTrack> match(Track track) {
        List<Platform> platforms = platformRepository.findByIsActiveTrue();

        for (Platform platform : platforms) {
            var existing = platformTrackRepository.findByTrackAndPlatform(track, platform);
            if (existing.isPresent() && isSourcePlatform(track, platform)) {
                continue;
            }
            existing.ifPresent(platformTrackRepository::delete);
            if (existing.isPresent()) {
                platformTrackRepository.flush();
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

    private boolean isSourcePlatform(Track track, Platform platform) {
        String sourceUrl = track.getSourceUrl();
        return sourceUrl != null && switch (platform.getSlug()) {
            case "spotify" -> sourceUrl.contains("spotify.com");
            case "ytmusic" -> sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be");
            case "apple" -> sourceUrl.contains("music.apple.com");
            case "melon" -> sourceUrl.contains("melon.com");
            case "flo" -> sourceUrl.contains("music-flo.com");
            case "genie" -> sourceUrl.contains("genie.co.kr");
            default -> false;
        };
    }

    private PlatformTrack matchToPlatform(Track track, Platform platform) {
        String sourceUrl = track.getSourceUrl();
        return switch (platform.getSlug()) {
            case "spotify" -> {
                if (sourceUrl != null && sourceUrl.contains("spotify.com")) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield spotifyTrackService.searchTrack(track, platform);
            }
            case "ytmusic" -> {
                if (sourceUrl != null && (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be"))) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield youtubeTrackService.searchTrack(track, platform);
            }
            case "apple" -> {
                if (sourceUrl != null && sourceUrl.contains("music.apple.com")) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield appleTrackService.searchTrack(track, platform);
            }
            case "melon"  -> {
                if (sourceUrl != null && sourceUrl.contains("melon.com")) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield melonTrackService.searchTrack(track, platform);
            }
            case "flo"    -> {
                if (sourceUrl != null && sourceUrl.contains("music-flo.com")) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield floTrackService.searchTrack(track, platform);
            }
            case "genie"  -> {
                if (sourceUrl != null && sourceUrl.contains("genie.co.kr")) {
                    yield new PlatformTrack(track, platform, null, sourceUrl, track.getTitle(), track.getArtist());
                }
                yield genieTrackService.searchTrack(track, platform);
            }
            default -> null;
        };
    }
}
