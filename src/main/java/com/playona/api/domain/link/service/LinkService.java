package com.playona.api.domain.link.service;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.service.AppleTrackService;
import com.playona.api.domain.track.service.SpotifyTrackService;
import com.playona.api.domain.track.service.TrackMatchingService;
import com.playona.api.domain.track.service.YoutubeTrackService;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserPlatformPreference;
import com.playona.api.domain.user.repository.UserPlatformPreferenceRepository;
import com.playona.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkService {

    @Value("${app.base-url}")
    private String baseUrl;

    private final UserRepository userRepository;
    private final AppleTrackService appleTrackService;
    private final YoutubeTrackService youtubeTrackService;
    private final SpotifyTrackService spotifyTrackService;
    private final SharedLinkRepository sharedLinkRepository;
    private final TrackMatchingService trackMatchingService;
    private final PlatformTrackRepository platformTrackRepository;
    private final UserPlatformPreferenceRepository userPlatformPreferenceRepository;

    @Transactional
    public LinkResponse createLink(String url) {
        Track track = findOrCreateTrack(url);

        User user = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String userUuid) {
            user = userRepository.findByUserUuid(userUuid).orElse(null);
        }

        Optional<SharedLink> existing = (user != null)
            ? sharedLinkRepository.findByTrackAndUser(track, user)
            : sharedLinkRepository.findFirstByTrackAndUserIsNull(track);

        if (existing.isPresent()) {
            return new LinkResponse(existing.get(), baseUrl, platformTrackRepository.findByTrack(existing.get().getTrack()));
        }

        String shortCode = generateShortCode();
        SharedLink sharedLink = new SharedLink(shortCode, track, user);
        sharedLinkRepository.save(sharedLink);
        trackMatchingService.matchAll(track);

        return new LinkResponse(sharedLink, baseUrl, platformTrackRepository.findByTrack(sharedLink.getTrack()));
    }

    private Track findOrCreateTrack(String url) {
        if (url.contains("spotify.com")) {
            return spotifyTrackService.getTrackFromUrl(url);
        } else if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return youtubeTrackService.getTrackFromUrl(url);
        } else if (url.contains("music.apple.com")) {
            return appleTrackService.getTrackFromUrl(url);
        }
        throw new IllegalArgumentException("지원하지 않는 플랫폼 URL입니다. (지원: Spotify, YouTube, Apple Music)");
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

    @Transactional
    public String getRedirectUrl(String shortCode, String userUuid) {
        SharedLink sharedLink = sharedLinkRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));
        sharedLink.incrementClickCount();
        sharedLinkRepository.save(sharedLink);

        if (userUuid == null) {
            return sharedLink.getTrack().getSourceUrl();
        }

        User user = userRepository.findByUserUuid(userUuid).orElse(null);
        if (user != null) {
            List<UserPlatformPreference> prefs = userPlatformPreferenceRepository
                .findByUserOrderByPriorityAsc(user);
            List<PlatformTrack> platformTracks = platformTrackRepository
                .findByTrack(sharedLink.getTrack());

            for (UserPlatformPreference pref : prefs) {
                Optional<PlatformTrack> match = platformTracks.stream()
                    .filter(pt -> pt.getPlatform().getId().equals(pref.getPlatform().getId()))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get().getUrl();
                }
            }
        }

        return sharedLink.getTrack().getSourceUrl();
    }

    public List<Map<String, String>> getPlatformUrls(String shortCode) {
        SharedLink sharedLink = sharedLinkRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));

        return platformTrackRepository.findByTrack(sharedLink.getTrack()).stream()
            .map(pt -> Map.of(
                "slug", pt.getPlatform().getSlug(),
                "name", pt.getPlatform().getName(),
                "url", pt.getUrl()
            ))
            .toList();
    }

    public List<LinkResponse> getMyLinks(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
            .map(user -> sharedLinkRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(link -> new LinkResponse(link, baseUrl, platformTrackRepository.findByTrack(link.getTrack())))
                .toList())
            .orElse(List.of());
    }
}
