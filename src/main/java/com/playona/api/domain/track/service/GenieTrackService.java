package com.playona.api.domain.track.service;

import static org.springframework.web.util.HtmlUtils.htmlUnescape;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenieTrackService {

    private final TrackRepository trackRepository;
    private final WebClient webClient = WebClient.create();

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

        String html = webClient
                .get()
                .uri(java.net.URI.create(sourceUrl))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (html == null) throw new RuntimeException("Genie 페이지 응답 없음: " + sourceUrl);

        Matcher titleMatcher = OG_TITLE.matcher(html);
        if (!titleMatcher.find()) throw new RuntimeException("Genie 곡 정보를 찾을 수 없습니다: " + sourceUrl);

        String[] metadata = extractTitleAndArtist(titleMatcher.group(1));
        String title = metadata[0];
        String artist = metadata[1];

        Matcher imageMatcher = OG_IMAGE.matcher(html);
        String thumbnail = imageMatcher.find() ? imageMatcher.group(1) : null;

        Track track = new Track(title, artist, thumbnail, sourceUrl);
        return trackRepository.save(track);
    }

    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        for (String query : TrackMatchVerifier.koreanSearchQueries(track)) {
            PlatformTrack result = searchQuery(track, platform, query);
            if (result != null) return result;
        }
        return null;
    }

    private PlatformTrack searchQuery(Track track, Platform platform, String rawQuery) {
        String query = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8).replace("+", "%20");

        try {
            String html = webClient
                    .get()
                    .uri(java.net.URI.create("https://www.genie.co.kr/search/searchMain?query=" + query))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.genie.co.kr/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            for (String songId : extractSearchSongIds(html)) {
                String detailUrl = "https://www.genie.co.kr/detail/songInfo?xgnm=" + songId;
                String detailHtml;
                try {
                    detailHtml = webClient.get().uri(java.net.URI.create(detailUrl))
                            .header("User-Agent", "Mozilla/5.0")
                            .retrieve().bodyToMono(String.class).block();
                } catch (Exception e) {
                    log.warn("[Genie] 후보 상세 조회 실패: songId={}", songId);
                    continue;
                }
                if (detailHtml == null) continue;
                Matcher metadataMatcher = OG_TITLE.matcher(detailHtml);
                if (!metadataMatcher.find()) continue;
                String[] metadata = extractTitleAndArtist(metadataMatcher.group(1));
                if (!TrackMatchVerifier.hasMatchingTitleAndArtist(
                        track.getTitle(), track.getArtist(), metadata[0], metadata[1])) continue;
                return new PlatformTrack(track, platform, songId, detailUrl, metadata[0], metadata[1]);
            }
        } catch (Exception e) {
            log.warn("[Genie] 검색 결과 직접 링크 추출 실패: {}", e.getMessage());
        }

        return null;
    }


    private static String[] extractTitleAndArtist(String value) {
        // "제목 / 아티스트 - genie" 형식 (songInfo, albumInfo 공통)
        String ogTitle = htmlUnescape(value);
        String stripped = ogTitle.replaceAll("\\s*-\\s*genie\\s*$", "").trim();
        int sep = stripped.lastIndexOf(" / ");
        String title  = sep > 0 ? stripped.substring(0, sep).trim() : stripped;
        String artist = sep > 0 ? stripped.substring(sep + 3).trim() : "";
        return new String[]{title, artist};
    }

    static String extractSongId(String url) {
        Matcher m = SONG_ID.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Could not extract Genie songId from URL: " + url);
    }

    static List<String> extractSearchSongIds(String html) {
        if (html == null) return List.of();
        Matcher matcher = Pattern.compile(
                "fnPlaySong\\('\\s*(\\d+)(?:;[^']*)?'\\s*,\\s*'1'\\)").matcher(html);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        while (matcher.find() && ids.size() < 5) ids.add(matcher.group(1));
        return List.copyOf(ids);
    }
}
