package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AppleArtistAliasTest {
  @Test
  void usesVerifiedKoreanTitleBeforeDomesticSearchAndRetainsEnglishAlias() throws Exception {
    for (String scenario : List.of("valid", "wrong-id", "wrong-date", "wrong-duration", "missing", "error")) {
      var service = new AppleTrackService(mock(TrackRepository.class));
      var mapper = new ObjectMapper();
      String english = mapper.writeValueAsString(Map.of("results", List.of(Map.of(
          "trackId", 7, "trackName", "Tell me (feat. Guest)", "artistName", "Artist",
          "trackTimeMillis", 148375, "releaseDate", "2024-12-17T00:00:00Z",
          "trackViewUrl", "https://music.apple.com/jp/song/example/7"))));
      String korean = mapper.writeValueAsString(Map.of("results", scenario.equals("missing") ? List.of() : List.of(Map.of(
          "trackId", scenario.equals("wrong-id") ? 8 : 7,
          "trackName", "말해줘 (feat. 게스트)", "artistName", "가수",
          "trackTimeMillis", scenario.equals("wrong-duration") ? 180000 : 148375,
          "releaseDate", scenario.equals("wrong-date") ? "2024-12-18T00:00:00Z" : "2024-12-17T00:00:00Z"))));
      ReflectionTestUtils.setField(service, "webClient", WebClient.builder().exchangeFunction(request -> {
        boolean krLookup = request.url().getPath().equals("/lookup") && request.url().getQuery().contains("country=kr");
        return Mono.just(ClientResponse.create(krLookup && scenario.equals("error") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
            .header("Content-Type", "application/json").body(krLookup ? korean : english).build());
      }).build());
      Track source = new Track("Tell me (feat. Guest)", "Artist", null, "https://music.youtube.com/watch?v=example");
      source.setDurationMs(149000);
      source.setReleaseDate(LocalDate.of(2024, 12, 17));
      service.enrichTopicMetadata(source);
      if (scenario.equals("valid")) {
        assertTrue(source.getTitle().startsWith("말해줘"));
        assertEquals(List.of("말해줘", "Tell me"), TrackMatchVerifier.searchTitles(source.getTitle()));
        assertTrue(TrackMatchVerifier.koreanSearchQueries(source).contains("말해줘 가수"));
        assertTrue(TrackMatchVerifier.hasMatchingArtist(source.getArtist(), "Artist"));
      } else assertEquals("Tell me (feat. Guest)", source.getTitle());
    }
  }

  @Test
  void learnsTopicTranslationWithKnownRecordingDurationAndReleaseDate() throws Exception {
    var service = new AppleTrackService(mock(TrackRepository.class));
    String response = new ObjectMapper().writeValueAsString(Map.of("results", List.of(Map.of(
        "trackId", 7, "trackName", "Morning", "artistName", "Artist", "trackTimeMillis", 199900,
        "releaseDate", "2026-03-30T00:00:00Z", "trackViewUrl", "https://music.apple.com/us/song/morning/7"))));
    ReflectionTestUtils.setField(service, "webClient", WebClient.builder().exchangeFunction(request -> Mono.just(
        ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(response).build())).build());
    Track topic = new Track("朝", "Artist", null, "https://music.youtube.com/watch?v=example");
    topic.setDurationMs(200000);
    topic.setReleaseDate(LocalDate.of(2026, 3, 30));
    service.enrichTopicMetadata(topic);
    assertEquals("朝 (Morning)", topic.getTitle());
    assertEquals(199900, topic.getDurationMs());
  }

  @Test
  void selectsKoreanStorefrontOnlyWhenSameCatalogIdIsAvailableThere() throws Exception {
    for (String scenario : List.of("available", "missing", "wrong-id", "foreign-url", "error")) {
      var service = new AppleTrackService(mock(TrackRepository.class));
      String response = new ObjectMapper().writeValueAsString(Map.of("results",
          scenario.equals("missing") ? List.of() : List.of(Map.of(
              "trackId", scenario.equals("wrong-id") ? 8 : 7,
              "trackName", "아침", "artistName", "가수",
              "trackViewUrl", "https://music.apple.com/" + (scenario.equals("foreign-url") ? "us" : "kr") + "/song/morning/7"))));
      ReflectionTestUtils.setField(service, "webClient", WebClient.builder().exchangeFunction(request -> {
        assertTrue(request.url().getQuery().contains("id=7"));
        assertTrue(request.url().getQuery().contains("country=kr"));
        return Mono.just(ClientResponse.create(scenario.equals("error") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
            .header("Content-Type", "application/json").body(response).build());
      }).build());
      Track source = new Track("Morning", "Artist", null, "https://example.com/track");
      PlatformTrack foreign = new PlatformTrack(source, new Platform(), "7",
          "https://music.apple.com/us/song/morning/7", "Morning", "Artist");
      PlatformTrack result = service.preferKoreanStorefront(foreign);
      if (scenario.equals("available")) {
        assertEquals("https://music.apple.com/kr/song/morning/7", result.getUrl());
        assertEquals("아침", result.getTitle());
        assertEquals("가수", result.getArtist());
      } else assertSame(foreign, result);
    }
  }

  @Test
  void readsAppleSourceInItsInputStorefront() throws Exception {
    var repository = mock(TrackRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service = new AppleTrackService(repository);
    String response = new ObjectMapper().writeValueAsString(Map.of("results", List.of(Map.of(
        "trackId", 7, "kind", "song", "trackName", "아침", "artistName", "가수",
        "trackViewUrl", "https://music.apple.com/kr/song/morning/7"))));
    ReflectionTestUtils.setField(service, "webClient", WebClient.builder().exchangeFunction(request -> {
      assertTrue(request.url().getQuery().contains("country=kr"));
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(response).build());
    }).build());
    Track result = service.getTrackFromUrl("https://music.apple.com/kr/song/morning/7");
    assertEquals("https://music.apple.com/kr/song/morning/7", result.getSourceUrl());
    assertEquals("아침", result.getTitle());
  }

  @Test
  void resolvesVideoRuntimeAndTranslatedTitleOnlyFromTheSameVerifiedRecording() throws Exception {
    for (String scenario : List.of("valid", "wrong-id", "wrong-artist", "wrong-date", "too-long")) {
      var service = new AppleTrackService(mock(TrackRepository.class));
      String search = new ObjectMapper().writeValueAsString(Map.of("results", List.of(
          Map.of("trackId", 1, "trackName", "朝 (Live)", "artistName", "Artist", "trackViewUrl", "https://example.com/1"),
          Map.of("trackId", 7, "trackName", "朝", "artistName", "Artist", "trackViewUrl", "https://example.com/7"))));
      String lookup = new ObjectMapper().writeValueAsString(Map.of("results", List.of(Map.of(
          "trackId", scenario.equals("wrong-id") ? 8 : 7,
          "trackName", "Morning", "artistName", scenario.equals("wrong-artist") ? "Other Artist" : "Artist",
          "trackTimeMillis", scenario.equals("too-long") ? 400000 : 240000,
          "releaseDate", scenario.equals("wrong-date") ? "2025-02-01T00:00:00Z" : "2025-01-01T00:00:00Z"))));
      ReflectionTestUtils.setField(service, "webClient", WebClient.builder().exchangeFunction(request -> {
        boolean isLookup = request.url().getPath().equals("/lookup");
        if (isLookup) assertTrue(request.url().getQuery().contains("id=7"));
        return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
            .body(isLookup ? lookup : search).build());
      }).build());
      Track video = new Track("朝", "Artist", null, "https://music.youtube.com/watch?v=example");
      video.setDurationMs(300000);
      video.setReleaseDate(LocalDate.of(2025, 1, 1));
      service.enrichMusicVideoMetadata(video);
      assertEquals(scenario.equals("valid") ? 240000 : 300000, video.getDurationMs());
      assertEquals(scenario.equals("valid") ? "朝 (Morning)" : "朝", video.getTitle());
      if (scenario.equals("valid")) {
        assertTrue(TrackMatchVerifier.searchTitles(video.getTitle()).containsAll(List.of("朝", "Morning")));
      }
    }
  }

  @Test
  void searchesByTitleWhenArtistNamesDifferButCatalogEvidenceMatches() {
    var repository = mock(TrackRepository.class);
    var service = new AppleTrackService(repository);
    var client = WebClient.builder().exchangeFunction(request -> {
      String query = URLDecoder.decode(request.url().getQuery(), StandardCharsets.UTF_8);
      String body = query.contains("term=メズマライザー") && !query.contains("32ki") && query.contains("country=jp")
          ? """
            {"results":[{"trackId":1745015575,"trackName":"メズマライザー",
              "artistName":"サツキ, 初音ミク & 重音テト","trackTimeMillis":156973,
              "releaseDate":"2024-05-17T12:00:00Z",
              "trackViewUrl":"https://music.apple.com/jp/album/x/1745015570?i=1745015575&uo=4"}]}
            """ : "{\"results\":[]}";
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build());
    }).build();
    ReflectionTestUtils.setField(service, "webClient", client);
    Track source = new Track("メズマライザー (feat. 初音ミク&重音テト)",
        "32ki, Hatsune Miku, 重音テト", null, "https://open.spotify.com/track/example");
    source.setDurationMs(156972);
    source.setReleaseDate(LocalDate.of(2024, 5, 17));
    var match = service.searchTrack(source, new Platform());
    assertNotNull(match);
    assertEquals("https://music.apple.com/jp/album/x/1745015570?i=1745015575", match.getUrl());
    verifyNoInteractions(repository);
  }

  @Test
  void searchesByExplicitTitleAliasWhenCatalogEvidenceMatches() {
    var repository = mock(TrackRepository.class);
    var service = new AppleTrackService(repository);
    var client = WebClient.builder().exchangeFunction(request -> {
      String query = URLDecoder.decode(request.url().getQuery(), StandardCharsets.UTF_8);
      String body = query.contains("term=eternal") && !query.contains("Yoon") && !query.contains("윤지영")
          && query.contains("country=us")
          ? """
            {"results":[
              {"trackId":1,"trackName":"eternal","artistName":"BOL4","trackTimeMillis":169380,"releaseDate":"2018-10-17T00:00:00Z","trackViewUrl":"https://music.apple.com/us/album/wrong/1?i=1"},
              {"trackId":1713353863,"trackName":"eternal","artistName":"Whys Young","trackTimeMillis":169380,"releaseDate":"2018-10-16T00:00:00Z","trackViewUrl":"https://music.apple.com/us/album/eternal/1713353861?i=1713353863&uo=4"}
            ]}
            """ : "{\"results\":[]}";
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build());
    }).build();
    ReflectionTestUtils.setField(service, "webClient", client);
    Track source = new Track("문득(eternal)", "윤지영(Yoon Jiyoung)", null, "https://www.youtube.com/watch?v=zv8BmkasGwM");
    source.setDurationMs(177000);
    source.setReleaseDate(LocalDate.of(2018, 10, 16));
    var match = service.searchTrack(source, new Platform());
    assertNotNull(match);
    assertEquals("https://music.apple.com/us/album/eternal/1713353861?i=1713353863", match.getUrl());
    assertEquals("윤지영(Yoon Jiyoung)", source.getArtist());
    assertEquals("문득(eternal)", source.getTitle());
    verifyNoInteractions(repository);
  }
}
