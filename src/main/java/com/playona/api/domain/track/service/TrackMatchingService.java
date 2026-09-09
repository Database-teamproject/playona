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
                if ("apple".equals(platform.getSlug())) {
                    PlatformTrack preferred = appleTrackService.preferKoreanStorefront(existing.get());
                    if (preferred != existing.get()) {
                        platformTrackRepository.delete(existing.get());
                        platformTrackRepository.flush();
                        platformTrackRepository.save(preferred);
                    }
                }
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
        if (isSourcePlatform(track, platform)) {
            PlatformTrack source = new PlatformTrack(track, platform, null, track.getSourceUrl(), track.getTitle(), track.getArtist());
            return "apple".equals(platform.getSlug()) ? appleTrackService.preferKoreanStorefront(source) : source;
        }
        return switch (platform.getSlug()) {
            case "spotify" -> spotifyTrackService.searchTrack(track, platform);
            case "ytmusic" -> youtubeTrackService.searchTrack(track, platform);
            case "apple" -> appleTrackService.searchTrack(track, platform);
            case "melon" -> melonTrackService.searchTrack(track, platform);
            case "flo" -> floTrackService.searchTrack(track, platform);
            case "genie" -> genieTrackService.searchTrack(track, platform);
            default -> null;
        };
    }
}
