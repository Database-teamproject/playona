package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SupportedMusicPlatformTest {

  @Test
  void identifiesEverySupportedPlatformByItsActualHost() {
    assertEquals(SupportedMusicPlatform.SPOTIFY,
        SupportedMusicPlatform.fromUrl("https://open.spotify.com/intl-ko/track/4uLU6hMCjMI75M1A2tKUQC"));
    assertEquals(SupportedMusicPlatform.YOUTUBE,
        SupportedMusicPlatform.fromUrl("https://m.youtube.com/shorts/aqz-KE-bpKQ"));
    assertEquals(SupportedMusicPlatform.APPLE_MUSIC,
        SupportedMusicPlatform.fromUrl("https://music.apple.com/kr/album/x/123?l=ko&i=456"));
    assertEquals(SupportedMusicPlatform.MELON,
        SupportedMusicPlatform.fromUrl("https://www.melon.com/song/detail.htm?songId=1"));
    assertEquals(SupportedMusicPlatform.FLO,
        SupportedMusicPlatform.fromUrl("https://www.music-flo.com/detail/track/1/details"));
    assertEquals(SupportedMusicPlatform.GENIE,
        SupportedMusicPlatform.fromUrl("https://www.genie.co.kr/detail/songInfo?xgnm=1"));
  }

  @Test
  void rejectsLookalikeHosts() {
    assertThrows(IllegalArgumentException.class,
        () -> SupportedMusicPlatform.fromUrl("https://spotify.com.example/track/id"));
  }

  @Test
  void extractsCommonSharedTrackUrlForms() {
    assertEquals("4uLU6hMCjMI75M1A2tKUQC", SpotifyTrackService.extractTrackId(
        "https://open.spotify.com/intl-ko/track/4uLU6hMCjMI75M1A2tKUQC?si=test"));
    assertEquals("aqz-KE-bpKQ", YoutubeTrackService.extractVideoId(
        "https://www.youtube.com/shorts/aqz-KE-bpKQ?feature=share"));
    assertEquals("aqz-KE-bpKQ", YoutubeTrackService.extractVideoId(
        "https://music.youtube.com/watch?v=aqz-KE-bpKQ&feature=share"));
    assertEquals("456", AppleTrackService.extractTrackId(
        "https://music.apple.com/kr/album/x/123?l=ko&i=456"));
    assertEquals("123", MelonTrackService.extractSongId(
        "https://www.melon.com/song/detail.htm?songId=123"));
    assertEquals("123", FloTrackService.extractTrackId(
        "https://www.music-flo.com/detail/track/123/details"));
    assertEquals("123", GenieTrackService.extractSongId(
        "https://www.genie.co.kr/detail/songInfo?xgnm=123"));
  }
}
