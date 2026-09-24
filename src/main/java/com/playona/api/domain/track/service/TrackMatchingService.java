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
    private final AiMatchAdvisor aiMatchAdvisor;

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
            try {
                PlatformTrack platformTrack = matchToPlatform(track, platform);
                if (platformTrack == null && existing.isPresent() && "apple".equals(platform.getSlug())) {
                    platformTrack = appleTrackService.preferKoreanStorefront(existing.get());
                }
                if (platformTrack != null) {
                    if (existing.isPresent()) {
                        if (java.util.Objects.equals(existing.get().getUrl(), platformTrack.getUrl())) continue;
                        platformTrackRepository.delete(existing.get());
                        platformTrackRepository.flush();
                    }
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
        if (!aiMatchAdvisor.enabled()) return legacyMatchToPlatform(track, platform);
        List<MatchCandidate> candidates;
        try {
            candidates = switch (platform.getSlug()) {
                case "spotify" -> spotifyTrackService.searchCandidates(track);
                case "ytmusic" -> youtubeTrackService.searchCandidates(track);
                case "apple" -> appleTrackService.searchCandidates(track);
                case "melon" -> melonTrackService.searchCandidates(track);
                case "flo" -> floTrackService.searchCandidates(track);
                case "genie" -> genieTrackService.searchCandidates(track);
                default -> List.of();
            };
        } catch (Exception e) {
            log.warn("AI 후보 검색 실패 - platform: {}, error: {}", platform.getSlug(), e.getMessage());
            candidates = List.of();
        }
        PlatformTrack baseline;
        try {
            baseline = "spotify".equals(platform.getSlug()) && !candidates.isEmpty()
                ? spotifyTrackService.firstVerifiedCandidate(track, platform, candidates)
                : legacyMatchToPlatform(track, platform);
        } catch (Exception e) {
            log.warn("기본 매칭 실패 - platform: {}, error: {}", platform.getSlug(), e.getMessage());
            baseline = null;
        }
        PlatformTrack chosen = aiMatchAdvisor.choose(track, platform, candidates, baseline);
        if ("spotify".equals(platform.getSlug()) && chosen != null) {
            String id = chosen.getPlatformTrackId();
            candidates.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .ifPresent(candidate -> spotifyTrackService.rememberIsrc(track, candidate));
        }
        if ("apple".equals(platform.getSlug()) && chosen != null && chosen != baseline) {
            return appleTrackService.preferKoreanStorefront(chosen);
        }
        return chosen;
    }

    private PlatformTrack legacyMatchToPlatform(Track track, Platform platform) {
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
