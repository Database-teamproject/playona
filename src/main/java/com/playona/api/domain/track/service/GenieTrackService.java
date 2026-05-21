package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GenieTrackService {

    private final TrackRepository trackRepository;

    private static final Pattern OG_TITLE = Pattern.compile("property=\"og:title\" content=\"([^\"]+)\"");
    private static final Pattern OG_IMAGE = Pattern.compile("property=\"og:image(?::secure_url)?\" content=\"(https://[^\"]+)\"");
    private static final Pattern SONG_ID   = Pattern.compile("[?&]xgnm=(\\d+)");
    private static final Pattern ALBUM_ID  = Pattern.compile("[?&]axnm=(\\d+)");

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Track getTrackFromUrl(String url) {
        final String sourceUrl;
        if (url.contains("albumInfo")) {
            Matcher m = ALBUM_ID.matcher(url);
            if (!m.find()) throw new IllegalArgumentException("Could not extract Genie albumId from URL: " + url);
            sourceUrl = "https://www.genie.co.kr/detail/albumInfo?axnm=" + m.group(1);
        } else {
            sourceUrl = "https://www.genie.co.kr/detail/songInfo?xgnm=" + extractSongId(url);
        }

        Track existing = trackRepository.findFirstBySourceUrl(sourceUrl).orElse(null);
        if (existing != null) return existing;

        String html = WebClient.create()
                .get()
                .uri(java.net.URI.create(sourceUrl))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (html == null) throw new RuntimeException("Genie 페이지 응답 없음: " + sourceUrl);

        Matcher titleMatcher = OG_TITLE.matcher(html);
        if (!titleMatcher.find()) throw new RuntimeException("Genie 곡 정보를 찾을 수 없습니다: " + sourceUrl);

        // "제목 / 아티스트 - genie" 형식 (songInfo, albumInfo 공통)
        String ogTitle = titleMatcher.group(1);
        String stripped = ogTitle.replaceAll("\\s*-\\s*genie\\s*$", "").trim();
        int sep = stripped.lastIndexOf(" / ");
        String title  = sep > 0 ? stripped.substring(0, sep).trim() : stripped;
        String artist = sep > 0 ? stripped.substring(sep + 3).trim() : "";

        Matcher imageMatcher = OG_IMAGE.matcher(html);
        String thumbnail = imageMatcher.find() ? imageMatcher.group(1) : null;

        Track track = new Track(title, artist, thumbnail, sourceUrl);
        return trackRepository.save(track);
    }

    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        String query = URLEncoder.encode(track.getTitle(), StandardCharsets.UTF_8).replace("+", "%20");
        String searchUrl = "https://www.genie.co.kr/search/searchMain?query=" + query;

        return new PlatformTrack(track, platform, null, searchUrl, track.getTitle(), track.getArtist());
    }

    private String extractSongId(String url) {
        Matcher m = SONG_ID.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Could not extract Genie songId from URL: " + url);
    }
}
