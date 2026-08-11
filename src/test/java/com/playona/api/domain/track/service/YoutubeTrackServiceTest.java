package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class YoutubeTrackServiceTest {

  @Test
  void acceptsOnlyNonLiveMusicCategorySourceVideos() {
    assertTrue(YoutubeTrackService.isAcceptedSourceVideo("10", "none"));
    assertTrue(YoutubeTrackService.isAcceptedSourceVideo("24", "none"));
    assertFalse(YoutubeTrackService.isAcceptedSourceVideo("10", "live"));
    assertFalse(YoutubeTrackService.isAcceptedSourceVideo("22", "none"));
  }

  @Test
  void requiresCandidateTitleToMatchTrackTitle() {
    assertTrue(YoutubeTrackService.isTitleClean("밤편지", "아이유", "아이유 밤편지"));
    assertFalse(YoutubeTrackService.isTitleClean("밤편지", "아이유", "아이유 좋은 날"));
  }

  @Test
  void rejectsCandidateWithIncompatibleDuration() {
    assertTrue(YoutubeTrackService.hasCompatibleDuration(210_000, 220_000L));
    assertFalse(YoutubeTrackService.hasCompatibleDuration(210_000, 226_000L));
  }
}
