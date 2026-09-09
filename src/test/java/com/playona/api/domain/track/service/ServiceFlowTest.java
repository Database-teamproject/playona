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
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void createsOrReusesLinksWithinUserScopeAndAlwaysRematches(boolean signedIn, boolean exists) {
    var users = mock(UserRepository.class);
    var tracks = mock(TrackService.class);
    var links = mock(SharedLinkRepository.class);
    var matching = mock(TrackMatchingService.class);
    var platformTracks = mock(PlatformTrackRepository.class);
    var service = new LinkService(users, tracks, mock(YoutubeTrackService.class), links,
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
    var service = new TrackMatchingService(platforms, matches, spotify, youtube, apple, melon, flo, genie);
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
}
