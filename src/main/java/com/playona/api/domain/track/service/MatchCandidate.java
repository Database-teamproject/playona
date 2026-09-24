package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import java.time.LocalDate;

record MatchCandidate(String id, String url, String title, String artist, String album,
    Integer durationMs, LocalDate releaseDate, String isrc) {

  PlatformTrack toPlatformTrack(Track track, Platform platform) {
    return new PlatformTrack(track, platform, id, url, title, artist);
  }
}
