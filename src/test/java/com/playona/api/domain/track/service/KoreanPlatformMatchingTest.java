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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class KoreanPlatformMatchingTest {

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
