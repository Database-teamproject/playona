package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackMatchVerifierTest {

  @Test
  void matchesExplicitAliasesInEitherDirection() {
    for (String title : new String[]{"문득", "eternal"}) {
      for (String artist : new String[]{"윤지영", "Yoon Jiyoung"}) {
        assertTrue(TrackMatchVerifier.hasMatchingTitleAndArtist(
            "문득(eternal)", "윤지영(Yoon Jiyoung)", title, artist));
        assertTrue(TrackMatchVerifier.hasMatchingTitleAndArtist(
            title, artist, "문득(eternal)", "윤지영(Yoon Jiyoung)"));
      }
    }
  }

  @Test
  void rejectsVersionsPartialTitlesAndWrongArtists() {
    for (String title : new String[]{"문득 (Live)", "문득 (Acoustic)", "문득 (DJ Remix)", "문득 다시"}) {
      assertFalse(TrackMatchVerifier.hasMatchingTitleAndArtist(
          "문득", "윤지영", title, "윤지영"));
    }
    assertFalse(TrackMatchVerifier.hasMatchingTitleAndArtist(
        "문득(eternal)", "윤지영(Yoon Jiyoung)", "Eternal love", "BOL4"));
    assertFalse(TrackMatchVerifier.hasMatchingTitleAndArtist(
        "Love", "Artist", "Lover", "Artist"));
    assertFalse(TrackMatchVerifier.hasMatchingTitleAndArtist(
        "문득(eternal)", "윤지영(Yoon Jiyoung)", "문득", "윤지영 밴드"));
  }

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

  @Test
  void acceptsExactFLOTitleAndArtistDespiteSourceVideoDuration() {
    assertTrue(TrackMatchVerifier.hasMatchingTitleAndArtist(
        "끝말잇기 (feat. 스키니 브라운)", "TOIL",
        "끝말잇기 (Feat. Skinny Brown)", "TOIL"));
  }
}
