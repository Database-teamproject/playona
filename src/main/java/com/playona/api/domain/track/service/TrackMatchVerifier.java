package com.playona.api.domain.track.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TrackMatchVerifier {

  private static final Pattern ALIAS = Pattern.compile("^([^()\\[\\]]+?)\\s*[（(]([^()（）]+)[）)]$");
  private static final Pattern VERSION = Pattern.compile(
      "(?i)\\b(live|remix|mix|acoustic|instrumental|inst|version|ver|edit|cover|demo|remaster(?:ed)?|sped|slowed|karaoke)\\b|라이브|리믹스|어쿠스틱|반주|버전");

  private TrackMatchVerifier() {}

  // Verified artist identity: https://linktr.ee/WhysYoung (윤지영 / Whys Young).
  // Keep curated aliases artist-only; never infer identity from a similar song title.
  private static final List<String> WHYS_YOUNG_NAMES = List.of("Whys Young", "윤지영", "Yoon Jiyoung");

  static List<String> artistNames(String artist) {
    List<String> explicit = names(firstArtist(artist));
    if (explicit.stream().anyMatch(name -> WHYS_YOUNG_NAMES.stream().anyMatch(alias -> isSimilar(name, alias)))) {
      return WHYS_YOUNG_NAMES;
    }
    return explicit;
  }

  static boolean hasMatchingArtist(String left, String right) {
    return artistNames(left).stream()
        .anyMatch(name -> artistNames(right).stream().anyMatch(candidate -> isSimilar(name, candidate)));
  }

  static boolean isConfidentMatch(String title, String artist, Integer durationMs,
      String candidateTitle, String candidateArtist, Integer candidateDurationMs) {
    return hasMatchingTitleAndArtist(title, artist, candidateTitle, candidateArtist)
        && hasCompatibleDuration(durationMs, candidateDurationMs);
  }

  static boolean hasMatchingTitleAndArtist(String title, String artist,
      String candidateTitle, String candidateArtist) {
    return isSimilar(title, candidateTitle)
        && hasMatchingArtist(artist, candidateArtist);
  }

  static boolean isSimilar(String left, String right) {
    return names(left).stream().map(TrackMatchVerifier::normalize)
        .filter(name -> !name.isEmpty())
        .anyMatch(name -> names(right).stream().map(TrackMatchVerifier::normalize).anyMatch(name::equals));
  }

  // Only explicit alternate scripts are aliases; version labels remain part of the title.
  static List<String> names(String value) {
    if (value == null) return List.of("");
    String cleaned = value.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|featuring)\\.?[^)\\]]*[\\)\\]]", "").trim();
    var alias = ALIAS.matcher(cleaned);
    if (alias.matches() && !VERSION.matcher(cleaned).find()) {
      String base = alias.group(1).trim();
      String alternate = alias.group(2).trim();
      if ((isLatin(base) && isAsian(alternate)) || (isAsian(base) && isLatin(alternate))) {
        return List.of(base, alternate);
      }
    }
    return List.of(cleaned);
  }

  private static boolean isLatin(String value) {
    return value.matches(".*[a-zA-Z].*") && !value.matches(".*[가-힣\\u3040-\\u30ff\\u4e00-\\u9fff].*");
  }

  private static boolean isAsian(String value) {
    return value.matches(".*[가-힣\\u3040-\\u30ff\\u4e00-\\u9fff].*") && !value.matches(".*[a-zA-Z].*");
  }

  private static boolean hasCompatibleDuration(Integer durationMs, Integer candidateDurationMs) {
    return durationMs == null || candidateDurationMs == null
        || Math.abs(durationMs.longValue() - candidateDurationMs) <= 15_000L;
  }

  static String firstArtist(String artist) {
    if (artist == null) return "";
    int depth = 0;
    for (int i = 0; i < artist.length(); i++) {
      char c = artist.charAt(i);
      if (c == '(' || c == '（') depth++;
      if (c == ')' || c == '）') depth--;
      if (depth == 0 && (c == ',' || c == '&')) return artist.substring(0, i).trim();
    }
    return artist.trim();
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|featuring)\\.?[^)\\]]*[\\)\\]]", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]", "");
  }
}
