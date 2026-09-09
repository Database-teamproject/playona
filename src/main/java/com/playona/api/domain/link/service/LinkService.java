package com.playona.api.domain.link.service;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.global.exception.NotFoundException;
import com.playona.api.domain.track.service.TrackMatchingService;
import com.playona.api.domain.track.service.TrackService;
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
    private final TrackService trackService;
    private final YoutubeTrackService youtubeTrackService;
    private final SharedLinkRepository sharedLinkRepository;
    private final TrackMatchingService trackMatchingService;
    private final PlatformTrackRepository platformTrackRepository;
    private final UserPlatformPreferenceRepository userPlatformPreferenceRepository;

    @Transactional
    public LinkResponse createLink(String url) {
        Track track = trackService.findOrCreateTrack(url);

        User user = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String userUuid) {
            user = userRepository.findByUserUuid(userUuid).orElse(null);
        }

        Optional<SharedLink> existing = (user != null)
            ? sharedLinkRepository.findByTrackAndUser(track, user)
            : sharedLinkRepository.findFirstByTrackAndUserIsNull(track);

        SharedLink sharedLink = existing.orElse(null);
        if (sharedLink == null) {
            sharedLink = sharedLinkRepository.save(new SharedLink(generateShortCode(), track, user));
        }
        trackMatchingService.matchAll(track);
        return new LinkResponse(sharedLink, baseUrl, platformTrackRepository.findByTrack(track));
    }

    private String generateShortCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (sharedLinkRepository.existsByShortCode(code));
        return code;
    }

    @Transactional(readOnly = true)
    public SharedLink getLink(String shortCode) {
        return sharedLinkRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new NotFoundException("링크를 찾을 수 없습니다: " + shortCode));
    }

    @Transactional(readOnly = true)
    public LinkResponse getLinkResponse(String shortCode) {
        SharedLink sharedLink = getLink(shortCode);
        return new LinkResponse(sharedLink, baseUrl, platformTrackRepository.findByTrack(sharedLink.getTrack()));
    }

    @Transactional
    public void incrementClickCount(String shortCode) {
        SharedLink sharedLink = getLink(shortCode);
        sharedLink.incrementClickCount();
        sharedLinkRepository.save(sharedLink);
    }

    @Transactional
    public String getRedirectUrl(String shortCode, String userUuid) {
        SharedLink sharedLink = getLink(shortCode);
        sharedLink.incrementClickCount();
        sharedLinkRepository.save(sharedLink);

        User user = userRepository.findByUserUuid(userUuid).orElse(null);
        if (user != null) {
            List<UserPlatformPreference> prefs = userPlatformPreferenceRepository
                .findByUserOrderByPriorityAsc(user);
            List<PlatformTrack> platformTracks = platformTrackRepository
                .findByTrack(sharedLink.getTrack());

            for (UserPlatformPreference pref : prefs) {
                Optional<PlatformTrack> match = platformTracks.stream()
                    .filter(pt -> !pt.isSearchFallback())
                    .filter(pt -> pt.getPlatform().getId().equals(pref.getPlatform().getId()))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get().getUrl();
                }
            }
        }

        return sharedLink.getTrack().getSourceUrl();
    }

    @Transactional
    public LinkResponse rematchLink(String shortCode, String userUuid) {
        User user = userRepository.findByUserUuid(userUuid)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        SharedLink link = sharedLinkRepository.findByShortCodeAndUser(shortCode, user)
            .orElseThrow(() -> new NotFoundException("링크를 찾을 수 없거나 재매칭 권한이 없습니다."));

        Track track = link.getTrack();
        if (track.getSourceUrl() != null
                && (track.getSourceUrl().contains("youtube.com") || track.getSourceUrl().contains("youtu.be"))) {
            track = youtubeTrackService.getTrackFromUrl(track.getSourceUrl());
        }
        platformTrackRepository.deleteByTrack(track);
        platformTrackRepository.flush();
        trackMatchingService.rematchAll(track);

        return new LinkResponse(link, baseUrl, platformTrackRepository.findByTrack(track));
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getPlatformUrls(String shortCode) {
        SharedLink sharedLink = getLink(shortCode);

        return platformTrackRepository.findByTrack(sharedLink.getTrack()).stream()
            .filter(pt -> !pt.isSearchFallback())
            .map(pt -> Map.of(
                "slug", pt.getPlatform().getSlug(),
                "name", pt.getPlatform().getName(),
                "url", pt.getUrl()
            ))
            .toList();
    }

    @Transactional
    public void deleteLink(String shortCode, String userUuid) {
        User user = userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        SharedLink link = sharedLinkRepository.findByShortCodeAndUser(shortCode, user)
                .orElseThrow(() -> new NotFoundException("링크를 찾을 수 없거나 삭제 권한이 없습니다."));
        sharedLinkRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> getMyLinks(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
            .map(user -> sharedLinkRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(link -> new LinkResponse(link, baseUrl, platformTrackRepository.findByTrack(link.getTrack())))
                .toList())
            .orElse(List.of());
    }
}
