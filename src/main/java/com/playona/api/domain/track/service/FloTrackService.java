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
import java.time.LocalDate;
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

        track.setDurationMs(parseDuration((String) data.get("playTime")));

        return trackRepository.save(track);
    }

    @SuppressWarnings("unchecked")
    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        for (String artist : TrackMatchVerifier.artistNames(track.getArtist())) {
            for (String title : TrackMatchVerifier.searchTitles(track.getTitle())) {
                PlatformTrack result = searchQuery(track, platform, title + " " + artist);
                if (result != null) return result;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private PlatformTrack searchQuery(Track track, Platform platform, String keyword) {

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

            if (response == null || !"2000000".equals(response.get("code"))) return null;
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("list");
            if (groups == null) return null;
            for (Map<String, Object> group : groups) {
                if (!"TRACK".equals(group.get("type"))) continue;
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) group.get("list");
                if (tracks == null) continue;
                for (Map<String, Object> candidate : tracks) {
                    Object id = candidate.get("id");
                    if (id == null || !isVerifiedMatch(track, candidate)) continue;
                    String trackUrl = "https://www.music-flo.com/detail/track/" + id + "/details";
                    List<Map<String, Object>> artists = (List<Map<String, Object>>) candidate.get("artistList");
                    return new PlatformTrack(track, platform, id.toString(), trackUrl,
                            (String) candidate.get("name"), (String) artists.get(0).get("name"));
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
        Integer durationMs = parseDuration((String) candidate.get("playTime"));
        Map<String, Object> album = (Map<String, Object>) candidate.get("album");
        LocalDate releaseDate = album == null ? null : parseReleaseDate((String) album.get("releaseYmd"));
        return TrackMatchVerifier.isEvidenceMatch(track, title, artist, durationMs, releaseDate);
    }

    private static Integer parseDuration(String value) {
        if (value == null || !value.matches("\\d+:\\d+")) return null;
        String[] parts = value.split(":");
        return (Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1])) * 1000;
    }

    private static LocalDate parseReleaseDate(String value) {
        if (value == null || !value.matches("\\d{8}")) return null;
        return LocalDate.parse(value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    static String extractTrackId(String url) {
        Matcher m = TRACK_ID.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Could not extract FLO trackId from URL: " + url);
    }
}
