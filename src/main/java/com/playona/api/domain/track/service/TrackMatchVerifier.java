package com.playona.api.domain.track.service;

final class TrackMatchVerifier {

  private TrackMatchVerifier() {}

  static boolean isConfidentMatch(String title, String artist, Integer durationMs,
      String candidateTitle, String candidateArtist, Integer candidateDurationMs) {
    return hasMatchingTitleAndArtist(title, artist, candidateTitle, candidateArtist)
        && hasCompatibleDuration(durationMs, candidateDurationMs);
  }

  static boolean hasMatchingTitleAndArtist(String title, String artist,
      String candidateTitle, String candidateArtist) {
    return isSimilar(title, candidateTitle)
        && isSimilar(firstArtist(artist), firstArtist(candidateArtist));
  }

  private static boolean isSimilar(String left, String right) {
    String normalizedLeft = normalize(left);
    String normalizedRight = normalize(right);
    if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) return false;
    int shorter = Math.min(normalizedLeft.length(), normalizedRight.length());
    int longer = Math.max(normalizedLeft.length(), normalizedRight.length());
    return shorter >= longer * 0.6
        && (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft));
  }

  private static boolean hasCompatibleDuration(Integer durationMs, Integer candidateDurationMs) {
    return durationMs == null || candidateDurationMs == null
        || Math.abs(durationMs.longValue() - candidateDurationMs) <= 15_000L;
  }

  private static String firstArtist(String artist) {
    return artist == null ? "" : artist.split("[,&]")[0];
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value
        .replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|featuring)\\.?[^)\\]]*[\\)\\]]", "")
        .toLowerCase()
        .replaceAll("[^a-z0-9가-힣\\u3040-\\u30ff\\u4e00-\\u9fff]", "");
  }
}
