package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.playona.api.domain.link.dto.LinkResponse;
import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import java.util.List;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class KoreanPlatformMatchingTest {

    @ParameterizedTest
    @ValueSource(strings = {"genie", "melon"})
    void skipsFailedAndWrongCandidatesBeforeReturningVerifiedSong(String slug) {
        TrackRepository repository = mock(TrackRepository.class);
        Object service = slug.equals("genie") ? new GenieTrackService(repository) : new MelonTrackService(repository);
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            String query = request.url().getQuery();
            boolean search = request.url().getPath().contains("/search");
            if (!search && query.endsWith("111")) {
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
            }
            String artist = query.endsWith("222") ? "Other Artist" : "Original Artist";
            String body = search
                    ? results(slug, "", "").replace("123", "111")
                        + results(slug, "", "").replace("123", "222")
                        + results(slug, "", "").replace("123", "333")
                    : "<meta property=\"og:title\" content=\"Morning"
                        + (slug.equals("genie") ? " / " : " - ") + artist
                        + (slug.equals("genie") ? " - genie" : "") + "\">";
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/html")
                    .body(body).build());
        }).build();
        ReflectionTestUtils.setField(service, "webClient", client);
        Track source = new Track("Morning", "Original Artist", null, "https://example.com/track");
        PlatformTrack result = slug.equals("genie")
                ? ((GenieTrackService) service).searchTrack(source, platform(slug))
                : ((MelonTrackService) service).searchTrack(source, platform(slug));
        assertNotNull(result);
        assertEquals("333", result.getPlatformTrackId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void retriesHyphenatedTitleAndDisplaysOnlyVerifiedResults(String slug) {
        Track source = new Track("soFt-dRink", "Mrs. GREEN APPLE", null, "https://music.youtube.com/watch?v=vt9YVvYFitg");
        for (String artist : List.of("Mrs. GREEN APPLE", "Other Artist")) {
            List<String> queries = new ArrayList<>();
            var match = search(slug, results(slug, "Softdrink", artist), "Softdrink", artist,
                    HttpStatus.OK, source, queries);
            assertTrue(queries.contains("soFt-dRink Mrs. GREEN APPLE"));
            assertTrue(queries.contains("soFtdRink Mrs. GREEN APPLE"));
            if (!slug.equals("flo")) assertTrue(queries.contains("soFt-dRink"));
            if (artist.equals("Other Artist")) {
                assertNull(match);
            } else {
                assertNotNull(match);
                assertEquals("Softdrink", match.getTitle());
                var response = new LinkResponse(new SharedLink("local", source), "http://localhost:3000", List.of(match));
                assertEquals(1, response.getPlatforms().size());
                assertEquals(match.getUrl(), response.getPlatforms().get(0).get("url"));
            }
        }
        assertEquals("soFt-dRink", source.getTitle());
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void matchesYoutubeBilingualMetadataWithoutChangingSource(String slug) {
        Track source = new Track("문득(eternal)", "윤지영(Yoon Jiyoung)", null,
                "https://www.youtube.com/watch?v=zv8BmkasGwM");
        assertNotNull(search(slug, results(slug, "문득", "윤지영"), "문득", "윤지영", HttpStatus.OK, source));
        assertNull(search(slug, results(slug, "문득 (Live)", "윤지영"), "문득 (Live)", "윤지영", HttpStatus.OK, source));
        assertEquals("문득(eternal)", source.getTitle());
        assertEquals("윤지영(Yoon Jiyoung)", source.getArtist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void excludesEmptyResultsAndApiErrors(String slug) {
        assertNull(search(slug, "", "", "", HttpStatus.OK));
        assertNull(search(slug, "", "", "", HttpStatus.TOO_MANY_REQUESTS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void rejectsWrongTitleOrArtist(String slug) {
        assertNull(search(slug, results(slug, "좋은 날", "아이유"), "좋은 날", "아이유", HttpStatus.OK));
        assertNull(search(slug, results(slug, "밤편지", "다른 가수"), "밤편지", "다른 가수", HttpStatus.OK));
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void returnsOnlyVerifiedDirectSongLinks(String slug) {
        PlatformTrack result = search(slug, results(slug, "밤편지", "아이유"), "밤편지", "아이유", HttpStatus.OK);
        assertNotNull(result);
        assertFalse(result.isSearchFallback());
        assertTrue(result.getUrl().contains("123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"flo", "genie", "melon"})
    void hidesPreviouslySavedSearchLinksFromResultResponse(String slug) {
        Track track = new Track("밤편지", "아이유", null, "https://music.youtube.com/watch?v=example");
        Platform platform = platform(slug);
        PlatformTrack fallback = new PlatformTrack(track, platform, null,
                "https://example.com/search?query=song", track.getTitle(), track.getArtist());
        PlatformTrack direct = new PlatformTrack(track, platform, "123",
                "https://example.com/detail/123", track.getTitle(), track.getArtist());
        LinkResponse response = new LinkResponse(new SharedLink("test", track), "https://playona.test",
                List.of(fallback, direct));
        assertEquals(1, response.getPlatforms().size());
        assertEquals(direct.getUrl(), response.getPlatforms().get(0).get("url"));
    }

    private PlatformTrack search(String slug, String searchBody, String title, String artist, HttpStatus status) {
        return search(slug, searchBody, title, artist, status,
                new Track("밤편지", "아이유", null, "https://music.youtube.com/watch?v=example"));
    }

    private PlatformTrack search(String slug, String searchBody, String title, String artist, HttpStatus status, Track track) {
        return search(slug, searchBody, title, artist, status, track, null);
    }

    private PlatformTrack search(String slug, String searchBody, String title, String artist, HttpStatus status, Track track,
            List<String> queries) {
        TrackRepository repository = mock(TrackRepository.class);
        Object service = switch (slug) {
            case "flo" -> new FloTrackService(repository);
            case "genie" -> new GenieTrackService(repository);
            default -> new MelonTrackService(repository);
        };
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            boolean search = request.url().getPath().contains("/search");
            String body = search ? searchBody : "<meta property=\"og:title\" content=\""
                    + title + (slug.equals("genie") ? " / " : " - ") + artist
                    + (slug.equals("genie") ? " - genie" : "") + "\">";
            if (search && queries != null) {
                String query = URLDecoder.decode(request.url().getRawQuery().split("&")[0].split("=", 2)[1], StandardCharsets.UTF_8);
                queries.add(query);
                if (query.contains("soFt-dRink")) {
                    body = slug.equals("flo") ? "{\"code\":\"2000000\",\"data\":{\"list\":[]}}" : "검색 결과 없음";
                }
            }
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", slug.equals("flo") ? "application/json" : "text/html")
                    .body(body).build());
        }).build();
        ReflectionTestUtils.setField(service, "webClient", client);
        Platform platform = platform(slug);
        PlatformTrack result = switch (slug) {
            case "flo" -> ((FloTrackService) service).searchTrack(track, platform);
            case "genie" -> ((GenieTrackService) service).searchTrack(track, platform);
            default -> ((MelonTrackService) service).searchTrack(track, platform);
        };
        verifyNoInteractions(repository);
        return result;
    }

    private String results(String slug, String title, String artist) {
        return switch (slug) {
            case "flo" -> "{\"code\":\"2000000\",\"data\":{\"list\":[{\"type\":\"TRACK\",\"list\":[{\"id\":123,\"name\":\""
                    + title + "\",\"artistList\":[{\"name\":\"" + artist + "\"}]}]}]}}";
            case "genie" -> "<a onclick=\"fnPlaySong('123','1')\">듣기</a>";
            default -> "<button data-song-no=\"123\">듣기</button>";
        };
    }

    private Platform platform(String slug) {
        Platform platform = new Platform();
        ReflectionTestUtils.setField(platform, "slug", slug);
        ReflectionTestUtils.setField(platform, "name", slug);
        return platform;
    }
}
