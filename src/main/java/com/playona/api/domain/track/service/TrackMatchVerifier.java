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
    String first = firstArtist(artist);
    var aliases = names(first);
    if (aliases.size() > 1) return aliases;
    // Some official credits place complete names in two scripts side by side.
    for (int i = 1; i < first.length(); i++) {
      if (!Character.isWhitespace(first.charAt(i))) continue;
      String left = first.substring(0, i).trim(), right = first.substring(i).trim();
      if ((isLatin(left) && isAsian(right)) || (isAsian(left) && isLatin(right))) {
        return List.of(left, right);
      }
    }
    return aliases;
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
      // Reissues can change album dates. An alternate-language title needs corroboration.
      return (isSimilar(names(source.getTitle()).get(0), names(candidateTitle).get(0))
          || hasCompatibleReleaseDate(source.getReleaseDate(), candidateReleaseDate))
          && hasCompatibleDuration(source.getDurationMs(), candidateDurationMs, 30_000L);
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

  static boolean isKoreanReleaseMatch(Track source, String candidateTitle, String candidateArtist,
      Integer candidateDurationMs, LocalDate candidateReleaseDate) {
    List<String> titles = names(source.getTitle());
    if (titles.size() != 2 || !titles.get(0).matches(".*[가-힣].*")
        || !candidateTitle.matches(".*[a-zA-Z].*")
        || !isSimilar(titles.get(1), candidateTitle)
        || !hasMatchingArtist(source.getArtist(), candidateArtist)
        || VERSION.matcher(source.getTitle()).find() || VERSION.matcher(candidateTitle).find()
        || source.getReleaseDate() == null || candidateReleaseDate == null
        || candidateReleaseDate.isBefore(source.getReleaseDate())
        || source.getDurationMs() == null || candidateDurationMs == null) return false;
    // ponytail: identical duration and verified title alias support a later catalog release;
    // recording IDs are needed to distinguish rare same-length language editions.
    return Math.abs(source.getDurationMs().longValue() - candidateDurationMs) <= 2_000L;
  }

  static boolean hasMatchingTitleAndArtist(String title, String artist,
      String candidateTitle, String candidateArtist) {
    return isSimilar(title, candidateTitle)
        && hasMatchingArtist(artist, candidateArtist);
  }

  static boolean hasConflictingVersionLabel(String left, String right) {
    return !VERSION.matcher(left == null ? "" : left).results()
        .map(match -> match.group().toLowerCase(Locale.ROOT)).toList()
        .equals(VERSION.matcher(right == null ? "" : right).results()
            .map(match -> match.group().toLowerCase(Locale.ROOT)).toList());
  }

  static boolean isSimilar(String left, String right) {
    return names(left).stream().map(TrackMatchVerifier::normalize)
        .filter(name -> !name.isEmpty())
        .anyMatch(name -> names(right).stream().map(TrackMatchVerifier::normalize).anyMatch(name::equals));
  }

  // Only explicit alternate scripts are aliases; version labels remain part of the title.
  static List<String> names(String value) {
    if (value == null) return List.of("");
    // Only a complete, trailing work credit is removable; recording version labels stay intact.
    String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("\\s*\\*\\s*(?:애니메이션|영화|드라마)\\s*\\[[^\\[\\]]+\\]\\s*(?:주제가|삽입곡|오프닝(?:\\s*테마)?|엔딩(?:\\s*테마)?)\\s*$", "")
        .replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|featuring)\\.?[^)\\]]*[\\)\\]]", "").trim();
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

  static boolean hasCompatibleReleaseDate(LocalDate source, LocalDate candidate) {
    return source == null || candidate == null
        || Math.abs(java.time.temporal.ChronoUnit.DAYS.between(source, candidate)) <= 1;
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
