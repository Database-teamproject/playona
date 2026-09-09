package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.playona.api.domain.track.entity.Track;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TrackMatchVerifierTest {

  @Test
  void rejectsMissingEvidenceForDiscrepantMetadata() {
    LocalDate date = LocalDate.of(2025, 1, 2);
    Track source = track("Morning", "Original Artist", 180000, date);
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Another Artist", null, date));
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Another Artist", 180000, null));
    source.setDurationMs(null);
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Another Artist", 180000, date));
    source.setDurationMs(180000);
    source.setReleaseDate(null);
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Another Artist", 180000, date));
  }

  @Test
  void rejectsDifferentSongsAndVersionsEvenWithMatchingDurationAndDate() {
    LocalDate date = LocalDate.of(2025, 1, 2);
    Track source = track("Morning", "Original Artist", 180000, date);
    for (String title : new String[]{"Evening", "Morning (Live)", "Morning (Remix)", "아침 (Acoustic)"}) {
      assertFalse(TrackMatchVerifier.isEvidenceMatch(source, title, "Original Artist", 180000, date));
    }
    source.setTitle("아침 (Live)");
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Original Artist", 180000, date));
    source.setTitle("아침");
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Original Artist", 190000, date));
  }

  @Test
  void acceptsDifferentArtistOnlyWithIndependentCatalogEvidence() {
    Track source = track("문득(eternal)", "윤지영(Yoon Jiyoung)", 177000,
        LocalDate.of(2018, 10, 16));
    assertTrue(TrackMatchVerifier.isEvidenceMatch(
        source, "eternal", "Whys Young", 169380, LocalDate.of(2018, 10, 16)));
    assertFalse(TrackMatchVerifier.hasMatchingArtist("Whys Young", "윤지영"));
    assertFalse(TrackMatchVerifier.isEvidenceMatch(
        source, "eternal", "Whys Young", 220000, LocalDate.of(2018, 10, 16)));
    assertFalse(TrackMatchVerifier.isEvidenceMatch(
        source, "eternal", "Whys Young", 169380, LocalDate.of(2018, 10, 17)));
  }

  @Test
  void acceptsCatalogTitleOrArtistVariationOnlyWithIndependentEvidence() {
    Track source = track("メズマライザー (feat. 初音ミク&重音テト)",
        "32ki, Hatsune Miku, 重音テト", 156972, LocalDate.of(2024, 5, 17));
    assertTrue(TrackMatchVerifier.isEvidenceMatch(source,
        "Mesmerizer (feat. Hatsune Miku&Kasane Teto)", "32ki", 157000,
        LocalDate.of(2024, 5, 17)));
    assertTrue(TrackMatchVerifier.isEvidenceMatch(source, "メズマライザー",
        "サツキ, 初音ミク & 重音テト", 156973, LocalDate.of(2024, 5, 17)));
    assertFalse(TrackMatchVerifier.isSimilar("32ki", "サツキ"));
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source,
        "Mesmerizer (Official English Version)", "サツキ", 156973, LocalDate.of(2024, 5, 17)));
    assertFalse(TrackMatchVerifier.isEvidenceMatch(source,
        "Mesmerizer", "Other Artist", 156973, LocalDate.of(2024, 5, 17)));
  }

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
    assertTrue(TrackMatchVerifier.isEvidenceMatch(
        track("밤편지", "아이유", 210_000, null), "밤편지", "아이유", 220_000, null));
  }

  @Test
  void rejectsSameTitleByDifferentArtist() {
    assertFalse(TrackMatchVerifier.isEvidenceMatch(
        track("Hello", "Adele", 295_000, null), "Hello", "Lionel Richie", 247_000, null));
  }

  @Test
  void rejectsSameSongWithDifferentDuration() {
    assertFalse(TrackMatchVerifier.isEvidenceMatch(
        track("밤편지", "아이유", 210_000, null), "밤편지", "아이유", 240_001, null));
  }

  @Test
  void allowsThirtySecondsOnlyWhenTitleAndArtistBothMatch() {
    LocalDate date = LocalDate.of(2026, 1, 12);
    Track source = track("Morning", "Artist", 300000, date);
    for (int duration : new int[]{270000, 330000}) {
      assertTrue(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Artist", duration, date));
      assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Other Artist", duration, date));
      assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning (Live)", "Artist", duration, date));
      assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "아침", "Artist", duration, date));
    }
    for (int duration : new int[]{269999, 330001}) {
      assertFalse(TrackMatchVerifier.isEvidenceMatch(source, "Morning", "Artist", duration, date));
    }
  }

  @Test
  void acceptsMatchingTitlesWhenFeaturingNamesUseDifferentScripts() {
    assertTrue(TrackMatchVerifier.isEvidenceMatch(
        track("끝말잇기 (feat. 스키니 브라운)", "TOIL", 224_000, null), "끝말잇기 (Feat. Skinny Brown)", "TOIL & Gist", 224_000, null));
  }

  @Test
  void acceptsExactFLOTitleAndArtistDespiteSourceVideoDuration() {
    assertTrue(TrackMatchVerifier.hasMatchingTitleAndArtist(
        "끝말잇기 (feat. 스키니 브라운)", "TOIL",
        "끝말잇기 (Feat. Skinny Brown)", "TOIL"));
  }

  private Track track(String title, String artist, int durationMs, LocalDate releaseDate) {
    Track track = new Track(title, artist, null, "https://example.com/source");
    track.setDurationMs(durationMs);
    track.setReleaseDate(releaseDate);
    return track;
  }
}
