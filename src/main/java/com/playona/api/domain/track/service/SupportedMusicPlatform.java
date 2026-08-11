package com.playona.api.domain.track.service;

import java.net.URI;
import java.util.Locale;

public enum SupportedMusicPlatform {
  SPOTIFY,
  YOUTUBE,
  APPLE_MUSIC,
  MELON,
  FLO,
  GENIE;

  public static SupportedMusicPlatform fromUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("음악 URL을 입력해주세요.");
    }

    final String host;
    try {
      host = URI.create(url.trim()).getHost();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("올바른 URL 형식이 아닙니다.");
    }

    if (host == null) {
      throw new IllegalArgumentException("올바른 URL 형식이 아닙니다.");
    }

    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (normalizedHost.equals("open.spotify.com")) return SPOTIFY;
    if (normalizedHost.equals("youtube.com") || normalizedHost.endsWith(".youtube.com")
        || normalizedHost.equals("youtu.be")) return YOUTUBE;
    if (normalizedHost.equals("music.apple.com")) return APPLE_MUSIC;
    if (normalizedHost.equals("melon.com") || normalizedHost.endsWith(".melon.com")) return MELON;
    if (normalizedHost.equals("music-flo.com") || normalizedHost.endsWith(".music-flo.com")) return FLO;
    if (normalizedHost.equals("genie.co.kr") || normalizedHost.endsWith(".genie.co.kr")) return GENIE;

    throw new IllegalArgumentException(
        "지원하지 않는 플랫폼 URL입니다. (지원: Spotify, YouTube, Apple Music, Melon, FLO, Genie)");
  }
}
