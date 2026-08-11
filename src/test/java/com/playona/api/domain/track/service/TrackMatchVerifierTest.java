package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackMatchVerifierTest {

  @Test
  void acceptsSameSongAndArtistWithSmallDurationDifference() {
    assertTrue(TrackMatchVerifier.isConfidentMatch(
        "밤편지", "아이유", 210_000, "밤편지", "아이유", 220_000));
  }

  @Test
  void rejectsSameTitleByDifferentArtist() {
    assertFalse(TrackMatchVerifier.isConfidentMatch(
        "Hello", "Adele", 295_000, "Hello", "Lionel Richie", 247_000));
  }

  @Test
  void rejectsSameSongWithDifferentDuration() {
    assertFalse(TrackMatchVerifier.isConfidentMatch(
        "밤편지", "아이유", 210_000, "밤편지", "아이유", 240_000));
  }

  @Test
  void acceptsMatchingTitlesWhenFeaturingNamesUseDifferentScripts() {
    assertTrue(TrackMatchVerifier.isConfidentMatch(
        "끝말잇기 (feat. 스키니 브라운)", "TOIL", 224_000,
        "끝말잇기 (Feat. Skinny Brown)", "TOIL & Gist", 224_000));
  }
}
