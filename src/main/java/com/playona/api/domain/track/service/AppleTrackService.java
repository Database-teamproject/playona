package com.playona.api.domain.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleTrackService {

  private final TrackRepository trackRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Track getTrackFromUrl(String url) {
    String trackId = extractTrackId(url);

    String lookupUrl = "https://itunes.apple.com/lookup?id=" +
            URLEncoder.encode(trackId, StandardCharsets.UTF_8) +
            "&entity=song";

    Map response = getAppleResponseAsMap(lookupUrl, "Failed to parse Apple lookup response");

    List results = (List) response.get("results");
    if (results == null || results.isEmpty()) {
      throw new RuntimeException("No Apple track found for ID: " + trackId);
    }

    Map item = null;

    for (Object obj : results) {
      Map row = (Map) obj;
      Object wrapperType = row.get("wrapperType");
      Object kind = row.get("kind");
      if ("track".equals(wrapperType) || "song".equals(kind)) {
        item = row;
        break;
      }
    }

    if (item == null) {
      item = (Map) results.get(0);
    }

    String title = (String) item.get("trackName");
    String artist = (String) item.get("artistName");
    String album = (String) item.get("collectionName");
    String isrc = (String) item.get("isrc");
    String sourceUrl = cleanAppleUrl((String) item.get("trackViewUrl"));
    String thumbnail = (String) item.get("artworkUrl100");

    Integer durationMs = null;
    if (item.get("trackTimeMillis") instanceof Integer ms) {
      durationMs = ms;
    } else if (item.get("trackTimeMillis") instanceof Number n) {
      durationMs = n.intValue();
    }

    LocalDate releaseDate = null;
    String releaseDateStr = (String) item.get("releaseDate");
    if (releaseDateStr != null && releaseDateStr.length() >= 10) {
      try {
        releaseDate = LocalDate.parse(releaseDateStr.substring(0, 10));
      } catch (Exception ignored) {
      }
    }

    if (isrc != null && trackRepository.existsByIsrc(isrc)) {
      return trackRepository.findByIsrc(isrc).orElseThrow();
    }

    Track existingTrack = trackRepository.findFirstBySourceUrl(sourceUrl).orElse(null);
    if (existingTrack != null) {
      return existingTrack;
    }

    Track newTrack = new Track(title, artist, thumbnail, sourceUrl, isrc);
    newTrack.setAlbum(album);
    newTrack.setDurationMs(durationMs);
    newTrack.setReleaseDate(releaseDate);

    return trackRepository.save(newTrack);
  }

  static String extractTrackId(String url) {
    if (url == null || !url.contains("music.apple.com/")) {
      throw new IllegalArgumentException("Not a valid Apple Music URL: " + url);
    }

    // album URL with selected song: ?i=1337452977
    var selectedSong = java.util.regex.Pattern.compile("[?&]i=(\\d+)").matcher(url);
    if (selectedSong.find()) return selectedSong.group(1);

    // direct /song/.../<id> or fallback numeric segment
    String[] parts = url.split("/");
    for (int i = parts.length - 1; i >= 0; i--) {
      String part = parts[i].split("\\?")[0];
      if (part.matches("\\d+")) {
        return part;
      }
    }

    throw new IllegalArgumentException("Could not extract Apple Music track ID from URL: " + url);
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    try {
      // 1. ISRC 기반 매칭 우선
      if (track.getIsrc() != null && !track.getIsrc().isBlank()) {
        PlatformTrack byIsrc = searchAppleByIsrc(track, platform, track.getIsrc());
        if (byIsrc != null) {
          // Apple KR 제목이 한국어이고 현재 Track 제목이 비한국어이면 업데이트
          // (Through the Night → 밤편지 등) — 이후 Melon/Genie 검색도 한국어로 진행됨
          updateToKoreanTitle(track, byIsrc.getTitle());
          return byIsrc;
        }
      }

      // 2. ISRC 없거나 조회 실패 시 title+artist 검색 (유사도 검사 포함)
      return searchAppleByTitleArtist(track, platform);

    } catch (Exception e) {
      throw new RuntimeException("Apple search failed: " + e.getMessage(), e);
    }
  }

  /** Spotify 등 글로벌 카탈로그 검색에 사용할 영문 곡명을 iTunes US에서 찾는다. */
  public String findGlobalTitle(Track track) {
    if (track.getTitle() == null || track.getArtist() == null) return null;

    try {
      String mainArtist = track.getArtist().split("[,&]")[0].trim();
      String query = URLEncoder.encode(track.getTitle() + " " + mainArtist, StandardCharsets.UTF_8)
          .replace("+", "%20");
      Map response = getAppleResponseAsMap(
          "https://itunes.apple.com/search?term=" + query + "&entity=song&limit=5&country=us",
          "Failed to parse Apple US search response");
      List results = (List) response.get("results");
      if (results == null) return null;

      for (Object obj : results) {
        Map item = (Map) obj;
        String title = (String) item.get("trackName");
        String artist = (String) item.get("artistName");
        if (title != null && isSimilar(mainArtist, artist)) {
          return title;
        }
      }
    } catch (Exception e) {
      log.warn("[Apple] 글로벌 곡명 조회 실패: {}", e.getMessage());
    }
    return null;
  }

  private PlatformTrack searchAppleByIsrc(Track track, Platform platform, String isrc) {
    String lookupUrl = "https://itunes.apple.com/lookup?isrc=" +
            URLEncoder.encode(isrc, StandardCharsets.UTF_8);

    Map response = getAppleResponseAsMap(lookupUrl, "Failed to parse Apple ISRC lookup response");

    List results = (List) response.get("results");
    if (results == null || results.isEmpty()) return null;

    Map item = (Map) results.get(0);

    // item.get("trackId")는 Integer — String.valueOf(null)은 "null"을 반환하므로 명시적 변환
    Object rawTrackId = item.get("trackId");
    String trackId = rawTrackId != null ? String.valueOf(rawTrackId) : null;
    String url = (String) item.get("trackViewUrl");
    String title = (String) item.get("trackName");
    String artist = (String) item.get("artistName");

    if (trackId == null || url == null) return null;

    // ISRC 결과 제목이 저장된 제목과 전혀 다르면 오매칭으로 간주 → title+artist 검색으로 폴백
    if (!isSimilar(track.getTitle(), title) && !isDifferentScript(track.getTitle(), title)) {
      log.warn("[Apple] ISRC 결과 제목 불일치 skip: '{}' vs '{}' (ISRC={})", track.getTitle(), title, isrc);
      return null;
    }

    return new PlatformTrack(track, platform, trackId, cleanAppleUrl(url), title, artist);
  }

  private PlatformTrack searchAppleByTitleArtist(Track track, Platform platform) {
    if (track.getTitle() == null || track.getArtist() == null) return null;

    // 다중 아티스트 쿼리는 iTunes 검색 품질 저하 → 첫 번째 아티스트만 사용
    String mainArtist = track.getArtist().split("[,&]")[0].trim();
    // (2025) / [2025] 연도 접미사 제거 후 나머지 괄호도 공백으로 교체
    String cleanTitle = track.getTitle()
        .replaceAll("[\\(\\[]\\d{4}[\\)\\]]", "")
        .replaceAll("[\\(\\)\\[\\]\\{\\}]", " ")
        .replaceAll("\\s+", " ").trim();
    String query = cleanTitle + " " + mainArtist;
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    log.info("[Apple] title+artist 검색 시작 - query: '{}', title: '{}', mainArtist: '{}'",
        query, track.getTitle(), mainArtist);

    // KR → US → JP 순서로 시도 (한국/글로벌 음원 우선, JP는 일본 전용 음원 폴백)
    for (String country : new String[]{"kr", "us", "jp"}) {
      String searchUrl = "https://itunes.apple.com/search?term=" + encoded
          + "&entity=song&limit=3&country=" + country;

      Map response = getAppleResponseAsMap(searchUrl, "Failed to parse Apple search response");
      List results = (List) response.get("results");
      log.info("[Apple] country={} resultCount={}", country, results == null ? 0 : results.size());
      if (results == null || results.isEmpty()) continue;

      for (Object obj : results) {
        Map item = (Map) obj;
        String resultTitle = (String) item.get("trackName");
        String resultArtist = (String) item.get("artistName");
        log.info("[Apple] 후보: title='{}' artist='{}'", resultTitle, resultArtist);

        // 제목 유사도 검사 (영어↔한국어 번역 제목은 스크립트 다름 허용)
        if (!isSimilar(track.getTitle(), resultTitle)
            && !isDifferentScript(track.getTitle(), resultTitle)) {
          log.info("[Apple] 제목 불일치 skip: '{}' vs '{}'", track.getTitle(), resultTitle);
          continue;
        }

        // 아티스트 비교: 양쪽 모두 첫 번째 아티스트만 추출
        String mainStoredArtist = track.getArtist() != null
            ? track.getArtist().split("[,&]")[0].trim() : "";
        String mainResultArtist = resultArtist != null
            ? resultArtist.split("[,&]")[0].trim() : "";
        if (!isSimilar(mainStoredArtist, mainResultArtist)
            && !isDifferentScript(mainStoredArtist, mainResultArtist)) {
          log.info("[Apple] 아티스트 불일치 skip: '{}' vs '{}'", mainStoredArtist, mainResultArtist);
          continue;
        }

        Object rawTrackId = item.get("trackId");
        String trackId = rawTrackId != null ? String.valueOf(rawTrackId) : null;
        String url = (String) item.get("trackViewUrl");
        if (trackId == null || url == null) continue;

        String krUrl = cleanAppleUrl(url);
        log.info("[Apple] 매칭 성공: '{}' - '{}'", resultTitle, krUrl);
        return new PlatformTrack(track, platform, trackId, krUrl, resultTitle, resultArtist);
      }
    }
    log.warn("[Apple] 매칭 실패 - title: '{}', artist: '{}'", track.getTitle(), track.getArtist());
    return null;
  }

  private boolean isSimilar(String a, String b) {
    if (a == null || b == null) return false;
    // 영문/숫자/한글/일본어(히라가나·카타카나·한자) 유지
    String na = a.toLowerCase().replaceAll("[^a-z0-9가-힣\\u3040-\\u30ff\\u4e00-\\u9fff]", "");
    String nb = b.toLowerCase().replaceAll("[^a-z0-9가-힣\\u3040-\\u30ff\\u4e00-\\u9fff]", "");
    if (na.isEmpty() || nb.isEmpty()) return false;
    // 짧은 쪽이 긴 쪽의 50% 이상이어야 하고, 한쪽이 다른 쪽을 포함해야 매칭
    int minLen = Math.min(na.length(), nb.length());
    int maxLen = Math.max(na.length(), nb.length());
    if (minLen < maxLen * 0.5) return false;
    return na.contains(nb) || nb.contains(na);
  }

  /** Track 제목이 비한국어이고 Apple KR 제목이 한국어이면 Track 제목 업데이트 */
  private void updateToKoreanTitle(Track track, String appleTitle) {
    if (appleTitle == null || track.getTitle() == null) return;
    boolean trackHasKorean = track.getTitle().matches(".*[가-힣].*");
    boolean appleHasKorean = appleTitle.matches(".*[가-힣].*");
    if (!trackHasKorean && appleHasKorean) {
      log.info("[Apple] 한국어 제목으로 업데이트: '{}' → '{}'", track.getTitle(), appleTitle);
      track.setTitle(appleTitle);
      trackRepository.save(track);
    }
  }

  /**
   * iTunes KR 검색으로 한국어 제목/아티스트 보정 (호출자 트랜잭션 참여).
   * TrackService.resolveTrack에서 matchAll 전에 호출 — 부모 @Transactional 컨텍스트에서 실행되어
   * trackRepository.save()가 부모 트랜잭션에 반영됨.
   */
  public void enrichKoreanMetadata(Track track) {
    if (track.getTitle() == null || track.getTitle().matches(".*[가-힣].*")) return;
    try {
      String mainArtist = track.getArtist() != null ? track.getArtist().split("[,&]")[0].trim() : "";
      String rawQuery = normalizeQuery(track.getTitle()) + (mainArtist.isBlank() ? "" : " " + normalizeQuery(mainArtist));
      String query = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8)
          .replace("+", "%20");
      String searchUrl = "https://itunes.apple.com/search?term=" + query
          + "&entity=song&limit=3&country=kr";

      Map response = getAppleResponseAsMap(searchUrl, "iTunes KR enrich failed");
      List results = (List) response.get("results");
      if (results == null || results.isEmpty()) return;

      for (Object obj : results) {
        Map item = (Map) obj;
        String krTitle = (String) item.get("trackName");
        String krArtist = (String) item.get("artistName");
        if (krTitle == null || !krTitle.matches(".*[가-힣].*")) continue;

        boolean updated = false;
        log.info("[Apple] KR enrichment 제목: '{}' → '{}'", track.getTitle(), krTitle);
        track.setTitle(krTitle);
        updated = true;

        if (krArtist != null && krArtist.matches(".*[가-힣].*")
            && track.getArtist() != null && !track.getArtist().matches(".*[가-힣].*")) {
          log.info("[Apple] KR enrichment 아티스트: '{}' → '{}'", track.getArtist(), krArtist);
          track.setArtist(krArtist);
        }
        if (updated) trackRepository.save(track);
        return;
      }
    } catch (Exception e) {
      log.warn("[Apple] KR enrichment 실패: {}", e.getMessage(), e);
    }
  }

  private static String normalizeQuery(String s) {
    if (s == null) return "";
    return s.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat|ft|prod|with)\\.?[^)\\]]*[\\)\\]]", "")
            .replaceAll("[‘’ʼ´`]", "'")
            .replaceAll("\\s+", " ").trim();
  }

  /** 아이유↔IU처럼 한쪽은 라틴, 다른쪽은 한글/CJK인 경우 true (동일 아티스트 가능성) */
  private boolean isDifferentScript(String a, String b) {
    if (a == null || b == null) return false;
    boolean aLatin = a.matches("[\\x00-\\x7F\\s]+");
    boolean bLatin = b.matches("[\\x00-\\x7F\\s]+");
    return aLatin != bLatin;
  }

  private String cleanAppleUrl(String url) {
    if (url == null) return null;
    return url.replaceFirst("music\\.apple\\.com/[a-z]{2}/", "music.apple.com/kr/")
              .replaceAll("\\?uo=\\d+&", "?")   // uo= 첫 번째 파라미터이고 뒤에 더 있는 경우
              .replaceAll("[?&]uo=\\d+", "")     // uo= 마지막 또는 유일한 파라미터인 경우
              .replaceAll("\\?$", "");
  }

  private Map getAppleResponseAsMap(String url, String errorMessage) {
    try {
      String responseBody = WebClient.create()
              .get()
              .uri(java.net.URI.create(url))
              .retrieve()
              .bodyToMono(String.class)
              .block();

      return objectMapper.readValue(responseBody, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(errorMessage + ": " + e.getMessage(), e);
    }
  }
}
