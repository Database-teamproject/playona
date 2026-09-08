package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.track.repository.TrackRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.global.exception.GlobalExceptionHandler;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class YoutubeTrackServiceTest {

  @Test
  void acceptsOfficialLyricVideoOnArtistChannelButRejectsFanUploadsAndOtherVersions() throws Exception {
    String title = "Mrs. GREEN APPLE「soFt-dRink」Official Lyric Video";
    assertFalse(YoutubeTrackService.isUnsupportedSourceVideo(title, "Mrs. GREEN APPLE"));
    assertTrue(YoutubeTrackService.isUnsupportedSourceVideo(title, "Fan Lyrics"));
    assertTrue(YoutubeTrackService.isUnsupportedSourceVideo(
        "Mrs. GREEN APPLE - soFt-dRink Lyrics", "Mrs. GREEN APPLE"));
    assertTrue(YoutubeTrackService.isUnsupportedSourceVideo(
        "Mrs. GREEN APPLE「soFt-dRink (Live)」Official Lyric Video", "Mrs. GREEN APPLE"));
    assertEquals(new YoutubeTrackService.SourceMetadata("soFt-dRink", "Mrs. GREEN APPLE"),
        YoutubeTrackService.extractSourceMetadata(title, "Mrs. GREEN APPLE"));

    TrackRepository repository = mock(TrackRepository.class);
    YoutubeTrackService service = new YoutubeTrackService(repository);
    String response = new ObjectMapper().writeValueAsString(Map.of("items", List.of(Map.of(
        "snippet", Map.of("categoryId", "10", "liveBroadcastContent", "none",
            "title", title, "channelTitle", "Mrs. GREEN APPLE", "description", ""),
        "contentDetails", Map.of("duration", "PT3M")))));
    ReflectionTestUtils.setField(service, "webClient", jsonClient(response));
    Track result = service.getTrackFromUrl("https://music.youtube.com/watch?v=vt9YVvYFitg&si=example");
    assertEquals("soFt-dRink", result.getTitle());
    assertEquals("Mrs. GREEN APPLE", result.getArtist());
    verify(repository).save(result);
  }

  private static final String COMPILATION_DESCRIPTION = """
      썸네일: 핀터레스트
      0:00 Je te laisserai des mots - Patrick Watson
      2:42 rises the moon - Liana Flores
      5:25 Here Comes a Thought(slowed) - Steven Universe
      9:13 Space Song - BeachHouse
      14:33 Roslyn - Bon Iver & St. Vincent
      """;

  @Test
  void detectsCompilationTitlesAndMultipleSongTimestamps() {
    assertTrue(YoutubeTrackService.isMusicCompilation("꿈", COMPILATION_DESCRIPTION));
    assertTrue(YoutubeTrackService.isMusicCompilation("𝐏𝐥𝐚𝐲𝐥𝐢𝐬𝐭 | 여름", null));
    assertTrue(YoutubeTrackService.isMusicCompilation("잔잔한 음악 모음", null));
    assertTrue(YoutubeTrackService.isMusicCompilation("Artist - Full Album", null));
    assertTrue(YoutubeTrackService.isMusicCompilation("꿈", "Tracklist\n0:00 First song\n3:00 Second song"));
  }

  @Test
  void acceptsSingleSongsWithoutTreatingChaptersOrPromotionalLinksAsCompilations() {
    assertFalse(YoutubeTrackService.isMusicCompilation(null, null));
    assertFalse(YoutubeTrackService.isMusicCompilation("아이유 - 밤편지 Official Music Video",
        "My playlist: https://youtube.com/playlist?list=example\n0:00 Intro\n0:30 Verse\n1:00 Chorus"));
    assertFalse(YoutubeTrackService.isMusicCompilation("꿈", "0:00 Song - Artist"));
    assertFalse(YoutubeTrackService.isMusicCompilation("꿈", "0:00 Song - Artist\n0:00 Song - Artist"));
    assertFalse(YoutubeTrackService.isMusicCompilation("꿈", "0:00 Song - Artist\n3:00 Song - Artist"));
  }

  @Test
  void extractsOnlyExplicitArtistTitleFormatsWithoutDiscardingPartsOfTitles() {
    assertEquals(new YoutubeTrackService.SourceMetadata("밤편지", "IU"),
        YoutubeTrackService.extractSourceMetadata("[MV] IU - 밤편지", "1theK"));
    assertEquals(new YoutubeTrackService.SourceMetadata("밤편지", "아이유"),
        YoutubeTrackService.extractSourceMetadata("아이유 - 밤편지 Official Music Video", "1theK"));
    assertEquals(new YoutubeTrackService.SourceMetadata("문득(eternal)", "윤지영(Yoon Jiyoung)"),
        YoutubeTrackService.extractSourceMetadata(
            "[MV] 윤지영(Yoon Jiyoung) - 문득(eternal) / Official Music Video", "POCLANOS"));
    assertEquals(new YoutubeTrackService.SourceMetadata("밤편지 (Live)", "아이유"),
        YoutubeTrackService.extractSourceMetadata("아이유 - 밤편지 (Live)", "아이유 - Topic"));
    assertNull(YoutubeTrackService.extractSourceMetadata("밤편지", "가사 채널"));
    assertEquals(new YoutubeTrackService.SourceMetadata("Square's dream (네모의 꿈)", "Artist"),
        YoutubeTrackService.extractSourceMetadata("Square's dream (네모의 꿈)", "Artist - Topic"));
  }

  @Test
  void refreshesExistingYoutubeMetadataBeforeReusingIt() throws Exception {
    TrackRepository repository = mock(TrackRepository.class);
    Track existing = new Track("MV", "1theK", null, "https://music.youtube.com/watch?v=15DI60dGMMo");
    when(repository.findFirstBySourceUrl(existing.getSourceUrl())).thenReturn(Optional.of(existing));
    YoutubeTrackService service = new YoutubeTrackService(repository);
    String response = new ObjectMapper().writeValueAsString(Map.of("items", List.of(Map.of(
        "snippet", Map.of("categoryId", "10", "liveBroadcastContent", "none",
            "title", "IU - 밤편지", "channelTitle", "1theK", "description", ""),
        "contentDetails", Map.of("duration", "PT4M13S")))));
    ReflectionTestUtils.setField(service, "webClient", jsonClient(response));

    Track refreshed = service.getTrackFromUrl(existing.getSourceUrl());

    assertSame(existing, refreshed);
    assertEquals("밤편지", refreshed.getTitle());
    assertEquals("IU", refreshed.getArtist());
    assertEquals(253_000, refreshed.getDurationMs());
    verify(repository).save(existing);
  }

  @Test
  void rejectsReportedVideoBeforeDatabaseAccessAndReturnsBadRequestMessage() throws Exception {
    TrackRepository repository = mock(TrackRepository.class);
    YoutubeTrackService service = new YoutubeTrackService(repository);
    String response = new ObjectMapper().writeValueAsString(Map.of("items", List.of(Map.of(
        "snippet", Map.of("categoryId", "10", "liveBroadcastContent", "none",
            "title", "꿈", "channelTitle", "Sea Pearl", "description", COMPILATION_DESCRIPTION),
        "contentDetails", Map.of("duration", "PT19M43S")))));
    ReflectionTestUtils.setField(service, "webClient", jsonClient(response));
    ReflectionTestUtils.setField(service, "apiKey", "test-key");

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> service.getTrackFromUrl("https://music.youtube.com/watch?v=15DI60dGMMo"));

    verifyNoInteractions(repository);
    var result = new GlobalExceptionHandler().handleIllegalArgument(error);
    assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    assertFalse(result.getBody().isSuccess());
    assertEquals("해당 링크는 음악 모음 영상이므로 통합 링크를 만들 수 없습니다.", result.getBody().getMessage());
  }

  @Test
  void acceptsOnlyNonLiveMusicCategorySourceVideos() {
    assertTrue(YoutubeTrackService.isAcceptedSourceVideo("10", "none"));
    assertTrue(YoutubeTrackService.isAcceptedSourceVideo("24", "none"));
    assertFalse(YoutubeTrackService.isAcceptedSourceVideo("10", "live"));
    assertFalse(YoutubeTrackService.isAcceptedSourceVideo("22", "none"));
  }

  @Test
  void requiresCandidateTitleToMatchTrackTitle() {
    assertTrue(YoutubeTrackService.isTitleClean("밤편지", "아이유", "밤편지"));
    assertFalse(YoutubeTrackService.isTitleClean("밤편지", "아이유", "아이유 좋은 날"));
  }

  @Test
  void rejectsCandidateWithIncompatibleDuration() {
    assertTrue(YoutubeTrackService.hasCompatibleDuration(210_000, 220_000L));
    assertFalse(YoutubeTrackService.hasCompatibleDuration(210_000, 241_000L));
  }

  @Test
  void requiresKnownDurationsAndDoesNotTreatWordFragmentsAsNoise() {
    assertFalse(YoutubeTrackService.hasCompatibleDuration(210_000, null));
    assertTrue(YoutubeTrackService.hasCompatibleDuration(210_000, 235_000L));
  }

  private WebClient jsonClient(String response) {
    return WebClient.builder().exchangeFunction(request -> Mono.just(
        ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
            .body(response).build())).build();
  }
}
