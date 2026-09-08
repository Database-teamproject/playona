package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FloTrackService {

    private final TrackRepository trackRepository;
    private final WebClient webClient = WebClient.create();

    private static final Pattern TRACK_ID = Pattern.compile("/detail/track/(\\d+)");

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Track getTrackFromUrl(String url) {
        String trackId = extractTrackId(url);
        String sourceUrl = "https://www.music-flo.com/detail/track/" + trackId + "/details";

        Track existing = trackRepository.findFirstBySourceUrl(sourceUrl).orElse(null);
        if (existing != null) return existing;

        String apiUrl = "https://www.music-flo.com/api/meta/v1/track/" + trackId;
        Map<String, Object> response = webClient
                .get()
                .uri(java.net.URI.create(apiUrl))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !"2000000".equals(response.get("code"))) {
            throw new RuntimeException("FLO API 응답 오류: " + trackId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        String title = (String) data.get("name");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artistList = (List<Map<String, Object>>) data.get("artistList");
        String artist = (artistList != null && !artistList.isEmpty())
                ? (String) artistList.get(0).get("name") : "";

        @SuppressWarnings("unchecked")
        Map<String, Object> album = (Map<String, Object>) data.get("album");
        String thumbnail = null;
        if (album != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> imgList = (List<Map<String, Object>>) album.get("imgList");
            if (imgList != null) {
                thumbnail = imgList.stream()
                        .filter(img -> {
                            Object size = img.get("size");
                            return size instanceof Number && ((Number) size).intValue() == 500;
                        })
                        .map(img -> (String) img.get("url"))
                        .findFirst()
                        .orElse(imgList.isEmpty() ? null : (String) imgList.get(0).get("url"));
            }
        }

        Track track = new Track(title, artist, thumbnail, sourceUrl);

        // "05:31" → milliseconds
        String playTime = (String) data.get("playTime");
        if (playTime != null && playTime.matches("\\d+:\\d+")) {
            String[] parts = playTime.split(":");
            int ms = (Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1])) * 1000;
            track.setDurationMs(ms);
        }

        return trackRepository.save(track);
    }

    @SuppressWarnings("unchecked")
    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        String mainArtist = TrackMatchVerifier.names(TrackMatchVerifier.firstArtist(track.getArtist())).get(0);
        String keyword = TrackMatchVerifier.names(track.getTitle()).get(0) + " " + mainArtist;

        try {
            String apiUrl = "https://www.music-flo.com/api/search/v2/search?keyword="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&searchType=TRACK&size=5";

            Map<String, Object> response = webClient
                    .get()
                    .uri(java.net.URI.create(apiUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && "2000000".equals(response.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("list");
                if (groups != null) {
                    for (Map<String, Object> group : groups) {
                        if ("TRACK".equals(group.get("type"))) {
                            List<Map<String, Object>> tracks = (List<Map<String, Object>>) group.get("list");
                            if (tracks != null && !tracks.isEmpty()) {
                                Map<String, Object> candidate = tracks.stream()
                                        .filter(item -> isVerifiedMatch(track, item))
                                        .findFirst()
                                        .orElse(null);
                                if (candidate == null) continue;
                                Object id = candidate.get("id");
                                if (id != null) {
                                    String trackUrl = "https://www.music-flo.com/detail/track/" + id + "/details";
                                    return new PlatformTrack(track, platform, null, trackUrl, track.getTitle(), track.getArtist());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean isVerifiedMatch(Track track, Map<String, Object> candidate) {
        String title = (String) candidate.get("name");
        List<Map<String, Object>> artists = (List<Map<String, Object>>) candidate.get("artistList");
        String artist = artists != null && !artists.isEmpty() ? (String) artists.get(0).get("name") : null;
        return TrackMatchVerifier.hasMatchingTitleAndArtist(
                track.getTitle(), track.getArtist(), title, artist);
    }

    static String extractTrackId(String url) {
        Matcher m = TRACK_ID.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Could not extract FLO trackId from URL: " + url);
    }
}
