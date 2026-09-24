package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.link.service.LinkService;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.platform.repository.PlatformRepository;
import com.playona.api.domain.platform.repository.PlatformTrackRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.repository.UserPlatformPreferenceRepository;
import com.playona.api.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceFlowTest {

  @ParameterizedTest
  @CsvSource({"kr,Apple Music", "us,Apple Music (미국 스토어)", "jp,Apple Music (일본 스토어)"})
  void labelsAppleStorefrontWithoutChangingTheUrl(String country, String name) {
    Platform platform = mock(Platform.class);
    when(platform.getSlug()).thenReturn("apple");
    when(platform.getName()).thenReturn("Apple Music");
    String url = "https://music.apple.com/" + country + "/song/123";
    PlatformTrack track = new PlatformTrack(null, platform, "123", url, "Song", "Artist");
    assertEquals(name, track.getDisplayName());
    assertEquals(url, track.getUrl());
  }

  @ParameterizedTest
  @CsvSource({"https://open.spotify.com/track/example", "https://music.apple.com/kr/song/7",
      "https://www.melon.com/song/detail.htm?songId=7", "https://www.music-flo.com/detail/track/7/details",
      "https://www.genie.co.kr/detail/songInfo?xgnm=7"})
  void enrichesEveryCatalogInputBeforeReturningItForMatching(String url) {
    var repository = mock(com.playona.api.domain.track.repository.TrackRepository.class);
    var apple = mock(AppleTrackService.class);
    var spotify = mock(SpotifyTrackService.class);
    var melon = mock(MelonTrackService.class);
    var flo = mock(FloTrackService.class);
    var genie = mock(GenieTrackService.class);
    Track track = new Track("Morning", "Artist", null, url);
    when(apple.getTrackFromUrl(url)).thenReturn(track);
    when(spotify.getTrackFromUrl(url)).thenReturn(track);
    when(melon.getTrackFromUrl(url)).thenReturn(track);
    when(flo.getTrackFromUrl(url)).thenReturn(track);
    when(genie.getTrackFromUrl(url)).thenReturn(track);
    when(repository.save(track)).thenReturn(track);
    var service = new TrackService(repository, mock(TrackMatchingService.class), mock(YoutubeTrackService.class),
        spotify, apple, melon, flo, genie);
    assertSame(track, service.findOrCreateTrack(url));
    var order = inOrder(apple, repository);
    order.verify(apple).enrichTopicMetadata(track);
    order.verify(repository).save(track);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void createsOrReusesLinksWithinUserScopeAndAlwaysRematches(boolean signedIn, boolean exists) {
    var users = mock(UserRepository.class);
    var tracks = mock(TrackService.class);
    var links = mock(SharedLinkRepository.class);
    var matching = mock(TrackMatchingService.class);
    var platformTracks = mock(PlatformTrackRepository.class);
    var service = new LinkService(users, tracks, links,
        matching, platformTracks, mock(UserPlatformPreferenceRepository.class));
    ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:3000");
    Track track = new Track("Morning", "Artist", null, "https://example.com/track");
    User user = signedIn ? mock(User.class) : null;
    SharedLink saved = new SharedLink("existing", track, user);
    when(tracks.findOrCreateTrack(track.getSourceUrl())).thenReturn(track);
    when(platformTracks.findByTrack(track)).thenReturn(List.of());
    when(links.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    SecurityContextHolder.clearContext();
    try {
      if (signedIn) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user-id", null, List.of()));
        when(users.findByUserUuid("user-id")).thenReturn(Optional.of(user));
        when(links.findByTrackAndUser(track, user)).thenReturn(exists ? Optional.of(saved) : Optional.empty());
      } else {
        when(links.findFirstByTrackAndUserIsNull(track)).thenReturn(exists ? Optional.of(saved) : Optional.empty());
      }
      var response = service.createLink(track.getSourceUrl());
      assertEquals("Morning", response.getTrackTitle());
      if (exists) assertEquals("existing", response.getShortCode());
      verify(links, times(exists ? 0 : 1)).save(any());
      verify(matching).matchAll(track);
      if (signedIn) verify(links, never()).findFirstByTrackAndUserIsNull(any());
      else verify(links, never()).findByTrackAndUser(any(), any());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @ParameterizedTest
  @CsvSource({
      "spotify,https://open.spotify.com/track/123",
      "ytmusic,https://music.youtube.com/watch?v=123",
      "apple,https://music.apple.com/kr/song/123",
      "melon,https://www.melon.com/song/detail.htm?songId=123",
      "flo,https://www.music-flo.com/detail/track/123/details",
      "genie,https://www.genie.co.kr/detail/songInfo?xgnm=123"
  })
  void keepsOriginalPlatformWithoutSearchingAndReusesSavedMatch(String slug, String sourceUrl) {
    var platforms = mock(PlatformRepository.class);
    var matches = mock(PlatformTrackRepository.class);
    var spotify = mock(SpotifyTrackService.class);
    var youtube = mock(YoutubeTrackService.class);
    var apple = mock(AppleTrackService.class);
    when(apple.preferKoreanStorefront(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var melon = mock(MelonTrackService.class);
    var flo = mock(FloTrackService.class);
    var genie = mock(GenieTrackService.class);
    var service = new TrackMatchingService(platforms, matches, spotify, youtube, apple, melon, flo, genie,
        mock(AiMatchAdvisor.class));
    Platform platform = new Platform();
    ReflectionTestUtils.setField(platform, "slug", slug);
    Track track = new Track("Morning", "Artist", null, sourceUrl);
    when(platforms.findByIsActiveTrue()).thenReturn(List.of(platform));

    service.matchAll(track);
    verify(matches).save(argThat(match -> sourceUrl.equals(match.getUrl()) && match.getTrack() == track));

    when(matches.findByTrackAndPlatform(track, platform))
        .thenReturn(Optional.of(new PlatformTrack(track, platform, null, sourceUrl, "Morning", "Artist")));
    service.matchAll(track);
    verify(matches, times(1)).save(any());
    verify(matches, never()).delete(any());
    verifyNoInteractions(spotify, youtube, melon, flo, genie);
    if (slug.equals("apple")) verify(apple, times(2)).preferKoreanStorefront(any());
    else verifyNoInteractions(apple);
  }

  @org.junit.jupiter.api.Test
  void keepsExistingMatchWhenRefreshFailsOrFindsNothing() {
    var platforms = mock(PlatformRepository.class);
    var matches = mock(PlatformTrackRepository.class);
    var spotify = mock(SpotifyTrackService.class);
    var service = new TrackMatchingService(platforms, matches, spotify,
        mock(YoutubeTrackService.class), mock(AppleTrackService.class),
        mock(MelonTrackService.class), mock(FloTrackService.class), mock(GenieTrackService.class),
        mock(AiMatchAdvisor.class));
    Platform platform = new Platform();
    ReflectionTestUtils.setField(platform, "slug", "spotify");
    Track track = new Track("Morning", "Artist", null, "https://music.youtube.com/watch?v=example");
    PlatformTrack existing = new PlatformTrack(track, platform, "old",
        "https://open.spotify.com/track/old", "Morning", "Artist");
    when(platforms.findByIsActiveTrue()).thenReturn(List.of(platform));
    when(matches.findByTrackAndPlatform(track, platform)).thenReturn(Optional.of(existing));

    service.matchAll(track);
    when(spotify.searchTrack(track, platform)).thenThrow(new RuntimeException("rate limited"));
    service.matchAll(track);

    verify(matches, never()).delete(existing);
    verify(matches, never()).flush();
  }

  @org.junit.jupiter.api.Test
  void storesAiSelectedCandidateAndItsVerifiedIsrc() {
    var platforms = mock(PlatformRepository.class);
    var matches = mock(PlatformTrackRepository.class);
    var spotify = mock(SpotifyTrackService.class);
    var advisor = mock(AiMatchAdvisor.class);
    var service = new TrackMatchingService(platforms, matches, spotify,
        mock(YoutubeTrackService.class), mock(AppleTrackService.class),
        mock(MelonTrackService.class), mock(FloTrackService.class), mock(GenieTrackService.class), advisor);
    Platform platform = new Platform();
    ReflectionTestUtils.setField(platform, "slug", "spotify");
    Track track = new Track("Morning", "Artist", null, "https://music.youtube.com/watch?v=example");
    MatchCandidate candidate = new MatchCandidate("better", "https://open.spotify.com/track/better",
        "Morning", "Artist", "Album", 180000, java.time.LocalDate.of(2025, 1, 1), "ISRC1");
    PlatformTrack selected = candidate.toPlatformTrack(track, platform);
    when(platforms.findByIsActiveTrue()).thenReturn(List.of(platform));
    when(advisor.enabled()).thenReturn(true);
    when(spotify.searchCandidates(track)).thenReturn(List.of(candidate));
    when(advisor.choose(eq(track), eq(platform), anyList(), isNull())).thenReturn(selected);

    service.matchAll(track);

    verify(matches).save(selected);
    verify(spotify).rememberIsrc(track, candidate);
    verify(spotify, never()).searchTrack(any(), any());
  }
}
