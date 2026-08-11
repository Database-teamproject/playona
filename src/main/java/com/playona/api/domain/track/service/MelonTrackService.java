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
public class MelonTrackService {

    private final TrackRepository trackRepository;

    private static final Pattern OG_TITLE = Pattern.compile("property=\"og:title\" content=\"([^\"]+)\"");
    private static final Pattern OG_IMAGE = Pattern.compile("property=\"og:image\" content=\"([^\"]+)\"");

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Track getTrackFromUrl(String url) {
        String songId = extractSongId(url);
        String detailUrl = "https://www.melon.com/song/detail.htm?songId=" + songId;
        String sourceUrl = detailUrl;

        Track existing = trackRepository.findFirstBySourceUrl(sourceUrl).orElse(null);
        if (existing != null) return existing;

        String html = WebClient.create()
                .get()
                .uri(java.net.URI.create(detailUrl))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (html == null) throw new RuntimeException("Melon 페이지 응답 없음: " + songId);

        Matcher titleMatcher = OG_TITLE.matcher(html);
        if (!titleMatcher.find()) throw new RuntimeException("Melon 곡 정보를 찾을 수 없습니다: " + songId);

        String ogTitle = unescapeHtml(titleMatcher.group(1));
        // "제목 - 아티스트" 형식에서 마지막 " - " 기준으로 분리
        int sep = ogTitle.lastIndexOf(" - ");
        String title = sep > 0 ? ogTitle.substring(0, sep).trim() : ogTitle.trim();
        String artist = sep > 0 ? ogTitle.substring(sep + 3).trim() : "";

        Matcher imageMatcher = OG_IMAGE.matcher(html);
        String thumbnail = imageMatcher.find() ? imageMatcher.group(1) : null;

        Track track = new Track(title, artist, thumbnail, sourceUrl);
        return trackRepository.save(track);
    }

    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        String mainArtist = track.getArtist() != null ? track.getArtist().split("[,&]")[0].trim() : "";
        String rawQuery = normalizeQuery(track.getTitle()) + (mainArtist.isBlank() ? "" : " " + normalizeQuery(mainArtist));
        String query = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackUrl = "https://www.melon.com/search/total/index.htm?q=" + query;

        return new PlatformTrack(track, platform, null, fallbackUrl, track.getTitle(), track.getArtist());
    }

    private static String normalizeQuery(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|prod|with)\\.?[^)\\]]*[\\)\\]]", "")
                .replaceAll("[\u2018\u2019\u02bc\u00b4`]", "'")
                .replaceAll("\\s+", " ").trim();
    }

    private static String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private String extractSongId(String url) {
        if (url == null || !url.contains("melon.com")) {
            throw new IllegalArgumentException("Not a valid Melon URL: " + url);
        }
        Pattern p = Pattern.compile("[?&]songId=(\\d+)");
        Matcher m = p.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Could not extract Melon songId from URL: " + url);
    }
}
