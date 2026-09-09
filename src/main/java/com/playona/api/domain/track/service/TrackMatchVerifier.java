package com.playona.api.domain.track.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

import com.playona.api.domain.track.entity.Track;

final class TrackMatchVerifier {

  private static final Pattern ALIAS = Pattern.compile("^([^()\\[\\]]+?)\\s*[（(]([^()（）]+)[）)]$");
  private static final Pattern VERSION = Pattern.compile(
      "(?i)\\b(live|remix|mix|acoustic|instrumental|inst|version|ver|edit|cover|demo|remaster(?:ed)?|sped|slowed|karaoke)\\b|라이브|리믹스|어쿠스틱|반주|버전");

  private TrackMatchVerifier() {}

  static List<String> searchTitles(String title) {
    var titles = new LinkedHashSet<String>();
    for (String name : names(title)) {
      titles.add(name);
      String withoutHyphens = name.replaceAll("[-‐‑‒–—]", "").replaceAll("\\s+", " ").trim();
      if (!withoutHyphens.isBlank()) titles.add(withoutHyphens);
    }
    return List.copyOf(titles);
  }

  static String explicitAlias(String original, String translated) {
    if (translated == null || translated.isBlank()) return original;
    String combined = original + " (" + translated + ")";
    return names(combined).size() == 2 ? combined : original;
  }

  static List<String> koreanSearchQueries(Track track) {
    String artist = normalizeQuery(artistNames(track.getArtist()).get(0));
    var queries = new LinkedHashSet<String>();
    for (String title : searchTitles(track.getTitle())) {
      String normalized = normalizeQuery(title);
      queries.add(artist.isBlank() ? normalized : normalized + " " + artist);
      queries.add(normalized);
    }
    return List.copyOf(queries);
  }

  private static String normalizeQuery(String value) {
    return value.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|prod|with)\\.?[^)\\]]*[\\)\\]]", "")
        .replaceAll("[‘’ʼ´`]", "'").replaceAll("\\s+", " ").trim();
  }

  static List<String> artistNames(String artist) {
    return names(firstArtist(artist));
  }

  static boolean hasMatchingArtist(String left, String right) {
    return artistNames(left).stream()
        .anyMatch(name -> artistNames(right).stream().anyMatch(candidate -> isSimilar(name, candidate)));
  }

  static boolean isEvidenceMatch(Track source, String candidateTitle, String candidateArtist,
      Integer candidateDurationMs, LocalDate candidateReleaseDate) {
    boolean titleMatches = isSimilar(source.getTitle(), candidateTitle);
    boolean artistMatches = hasMatchingArtist(source.getArtist(), candidateArtist);
    if (titleMatches && artistMatches) {
      return hasCompatibleDuration(source.getDurationMs(), candidateDurationMs, 30_000L);
    }
    if (!(titleMatches || artistMatches)) return false;
    if (!titleMatches) {
      String sourceTitle = names(source.getTitle()).get(0);
      String targetTitle = names(candidateTitle).get(0);
      if (VERSION.matcher(sourceTitle).find() || VERSION.matcher(targetTitle).find()) return false;
      // A different title in the same script is not evidence of a translation.
      if (!((isLatin(sourceTitle) && isAsian(targetTitle))
          || (isAsian(sourceTitle) && isLatin(targetTitle)))) return false;
    }
    // ponytail: metadata corroboration is heuristic; use recording IDs when same-date tracks collide.
    // Missing metadata cannot corroborate a title or artist discrepancy.
    return source.getDurationMs() != null && source.getDurationMs() > 0
        && candidateDurationMs != null && candidateDurationMs > 0
        && (titleMatches || Math.abs(source.getDurationMs().longValue() - candidateDurationMs) <= 2_000L)
        && hasCompatibleDuration(source.getDurationMs(), candidateDurationMs, 15_000L)
        && hasMatchingReleaseDate(source.getReleaseDate(), candidateReleaseDate);
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

  private static boolean hasCompatibleDuration(Integer durationMs, Integer candidateDurationMs, long toleranceMs) {
    return durationMs == null || candidateDurationMs == null
        || Math.abs(durationMs.longValue() - candidateDurationMs) <= toleranceMs;
  }

  private static boolean hasMatchingReleaseDate(LocalDate releaseDate, LocalDate candidateReleaseDate) {
    return releaseDate != null && releaseDate.equals(candidateReleaseDate);
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
