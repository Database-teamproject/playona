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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleTrackService {

  private final WebClient webClient = WebClient.create();

  private final TrackRepository trackRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Track getTrackFromUrl(String url) {
    String trackId = extractTrackId(url);

    String lookupUrl = "https://itunes.apple.com/lookup?id=" +
            URLEncoder.encode(trackId, StandardCharsets.UTF_8) +
            "&entity=song&country=" + storefront(url);

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

    Integer durationMs = item.get("trackTimeMillis") instanceof Number n ? n.intValue() : null;

    LocalDate releaseDate = null;
    String releaseDateStr = (String) item.get("releaseDate");
    if (releaseDateStr != null && releaseDateStr.length() >= 10) {
      try {
        releaseDate = LocalDate.parse(releaseDateStr.substring(0, 10));
      } catch (Exception ignored) {
      }
    }

    if (isrc != null) {
      var existing = trackRepository.findFirstByIsrcOrderByIdAsc(isrc);
      if (existing.isPresent()) return existing.get();
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

  /** Video runtime includes footage; resolve recording length using exact catalog identity first. */
  public void enrichMusicVideoMetadata(Track track) {
    enrichRecordingMetadata(track, true);
  }

  public void enrichTopicMetadata(Track track) {
    enrichRecordingMetadata(track, false);
  }

  private void enrichRecordingMetadata(Track track, boolean musicVideo) {
    if (track.getReleaseDate() == null || (musicVideo && track.getDurationMs() == null)) return;
    try {
      // Only music videos omit runtime in the probe; Topic audio keeps duration/date verification.
      Track probe = musicVideo ? new Track(track.getTitle(), track.getArtist(), null, null) : track;
      boolean appleSource = track.getSourceUrl() != null && track.getSourceUrl().contains("music.apple.com/");
      PlatformTrack recording = appleSource
          ? new PlatformTrack(track, null, extractTrackId(track.getSourceUrl()), track.getSourceUrl(), track.getTitle(), track.getArtist())
          : searchAppleByTitleArtist(probe, null);
      if (recording == null) return;
      recording = preferKoreanStorefront(recording);
      String id = recording.getPlatformTrackId();
      LocalDate originalDate = track.getReleaseDate();
      String originalArtist = track.getArtist();
      for (String country : new java.util.LinkedHashSet<>(List.of("kr", "us", "jp", storefront(recording.getUrl())))) {
        try {
          Map response = getAppleResponseAsMap("https://itunes.apple.com/lookup?id="
              + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&entity=song&country=" + country,
              "Failed to read recording metadata");
          if (!(response.get("results") instanceof List results)) continue;
          for (Object result : results) {
            Map item = (Map) result;
            if (!id.equals(String.valueOf(item.get("trackId")))
                || (!appleSource && !("kr".equals(country)
                    && String.valueOf(item.get("artistName")).matches(".*[가-힣].*"))
                    && !TrackMatchVerifier.hasMatchingArtist(originalArtist, (String) item.get("artistName")))) continue;
            LocalDate released = parseReleaseDate((String) item.get("releaseDate"));
            // Official videos can precede a recording release; Topic/catalog dates must agree exactly.
            if (released == null || (musicVideo
                ? Math.abs(java.time.temporal.ChronoUnit.DAYS.between(originalDate, released)) > 30
                : !originalDate.equals(released))) continue;
            if (!(item.get("trackTimeMillis") instanceof Number duration)
                || duration.intValue() <= 0
                || (musicVideo ? duration.intValue() > track.getDurationMs()
                    : track.getDurationMs() != null && Math.abs(duration.longValue() - track.getDurationMs()) > 2_000L)) continue;
            String title = (String) item.get("trackName");
            if (title == null || title.isBlank()) continue;
            track.setDurationMs(duration.intValue());
            track.setReleaseDate(released);
            if ("kr".equals(country) && title.matches(".*[가-힣].*")) {
              // The verified catalog ID links localized names; keep the original searchable as an alias.
              track.setTitle(TrackMatchVerifier.explicitAlias(title, track.getTitle()));
              String artist = (String) item.get("artistName");
              if (artist != null && !artist.isBlank()) {
                track.setArtist(TrackMatchVerifier.explicitAlias(artist, track.getArtist()));
              }
            } else {
              track.setTitle(TrackMatchVerifier.explicitAlias(track.getTitle(), title));
              track.setArtist(TrackMatchVerifier.explicitAlias(track.getArtist(), (String) item.get("artistName")));
            }
          }
        } catch (Exception e) {
          log.warn("[Apple] {} 음원 메타데이터 확인 실패: {}", country, e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[Apple] 영상의 음원 메타데이터 확인 실패: {}", e.getMessage());
    }
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    try {
      // 1. ISRC 기반 매칭 우선
      if (track.getIsrc() != null && !track.getIsrc().isBlank()) {
        PlatformTrack byIsrc = searchAppleByIsrc(track, platform, track.getIsrc());
        if (byIsrc != null) {
          return preferKoreanStorefront(byIsrc);
        }
      }

      // 2. ISRC 없거나 조회 실패 시 title+artist 검색 (유사도 검사 포함)
      return preferKoreanStorefront(searchAppleByTitleArtist(track, platform));

    } catch (Exception e) {
      throw new RuntimeException("Apple search failed: " + e.getMessage(), e);
    }
  }

  List<MatchCandidate> searchCandidates(Track track) {
    if (track.getTitle() == null || track.getArtist() == null) return List.of();
    var queries = new LinkedHashSet<String>();
    for (String artist : TrackMatchVerifier.artistNames(track.getArtist())) {
      for (String title : TrackMatchVerifier.searchTitles(track.getTitle())) queries.add(title + " " + artist);
    }
    queries.addAll(TrackMatchVerifier.searchTitles(track.getTitle()));
    var found = new LinkedHashMap<String, MatchCandidate>();
    for (String query : queries) {
      String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
      for (String country : List.of("kr", "us", "jp")) {
        try {
          Map response = getAppleResponseAsMap("https://itunes.apple.com/search?term=" + encoded
              + "&entity=song&limit=15&country=" + country, "Failed to read Apple candidates");
          if (!(response.get("results") instanceof List results)) continue;
          for (Object value : results) {
            Map item = (Map) value;
            String id = String.valueOf(item.get("trackId"));
            String url = (String) item.get("trackViewUrl");
            if ("null".equals(id) || url == null || found.containsKey(id)
                || !"music.apple.com".equals(URI.create(url).getHost()) || !id.equals(extractTrackId(url))) continue;
            found.put(id, new MatchCandidate(id, cleanAppleUrl(url), (String) item.get("trackName"),
                (String) item.get("artistName"), (String) item.get("collectionName"),
                item.get("trackTimeMillis") instanceof Number n ? n.intValue() : null,
                parseReleaseDate((String) item.get("releaseDate")), (String) item.get("isrc")));
            if (found.size() >= 20) return new ArrayList<>(found.values());
          }
        } catch (Exception e) {
          log.warn("[Apple] 후보 검색 실패: country={}, error={}", country, e.getClass().getSimpleName());
        }
      }
    }
    return new ArrayList<>(found.values());
  }

  public PlatformTrack preferKoreanStorefront(PlatformTrack match) {
    if (match == null || "kr".equals(storefront(match.getUrl()))) return match;
    try {
      String id = extractTrackId(match.getUrl());
      Map response = getAppleResponseAsMap("https://itunes.apple.com/lookup?id=" + id
          + "&entity=song&country=kr", "Failed to check Korean storefront");
      if (!(response.get("results") instanceof List results)) return match;
      for (Object result : results) {
        Map item = (Map) result;
        String url = (String) item.get("trackViewUrl");
        if (!id.equals(String.valueOf(item.get("trackId"))) || url == null
            || !"music.apple.com".equals(java.net.URI.create(url).getHost())
            || !"kr".equals(storefront(url)) || !id.equals(extractTrackId(url))) continue;
        // A catalog ID establishes identity across localized titles and artist names.
        return new PlatformTrack(match.getTrack(), match.getPlatform(), id, cleanAppleUrl(url),
            (String) item.get("trackName"), (String) item.get("artistName"));
      }
      return findKoreanRecording(match, id);
    } catch (Exception e) {
      log.warn("[Apple] 한국 스토어 확인 실패: {}", e.getMessage());
    }
    return match;
  }

  private PlatformTrack findKoreanRecording(PlatformTrack match, String id) {
    Map originalResponse = getAppleResponseAsMap("https://itunes.apple.com/lookup?id=" + id
        + "&entity=song&country=" + storefront(match.getUrl()), "Failed to read source recording");
    if (!(originalResponse.get("results") instanceof List originals)) return match;
    for (Object value : originals) {
      Map original = (Map) value;
      if (!id.equals(String.valueOf(original.get("trackId")))
          || !(original.get("artistId") instanceof Number artistId)
          || !(original.get("trackTimeMillis") instanceof Number duration)) continue;
      LocalDate date = parseReleaseDate((String) original.get("releaseDate"));
      if (date == null || duration.longValue() <= 0) return match;
      Track probe = new Track(match.getTrack() == null ? match.getTitle() : match.getTrack().getTitle(),
          (String) original.get("artistName"), null, null);
      probe.setDurationMs(duration.intValue());
      probe.setReleaseDate(date);
      Map response = getAppleResponseAsMap("https://itunes.apple.com/lookup?id=" + artistId
          + "&entity=song&country=kr&limit=200", "Failed to read Korean artist catalog");
      if (!(response.get("results") instanceof List candidates)) return match;
      PlatformTrack verified = null;
      // ponytail: bounded artist catalog; ambiguous or unlisted recordings retain the original link.
      for (Object candidate : candidates) {
        Map item = (Map) candidate;
        String url = (String) item.get("trackViewUrl");
        String candidateId = String.valueOf(item.get("trackId"));
        if (!(item.get("artistId") instanceof Number candidateArtist) || candidateArtist.longValue() != artistId.longValue()
            || !(item.get("trackTimeMillis") instanceof Number candidateDuration)
            || candidateDuration.longValue() <= 0 || Math.abs(duration.longValue() - candidateDuration.longValue()) > 2_000L
            || !date.equals(parseReleaseDate((String) item.get("releaseDate")))
            || url == null || !"music.apple.com".equals(java.net.URI.create(url).getHost())
            || !"kr".equals(storefront(url)) || !candidateId.equals(extractTrackId(url))
            || !TrackMatchVerifier.isEvidenceMatch(probe, (String) item.get("trackName"),
                probe.getArtist(), candidateDuration.intValue(), date)) continue;
        if (verified != null && !verified.getPlatformTrackId().equals(candidateId)) return match;
        verified = new PlatformTrack(match.getTrack(), match.getPlatform(), candidateId, cleanAppleUrl(url),
            (String) item.get("trackName"), (String) item.get("artistName"));
      }
      return verified == null ? match : verified;
    }
    return match;
  }

  private static String storefront(String url) {
    var region = java.util.regex.Pattern.compile("^https?://music\\.apple\\.com/([a-z]{2})/")
        .matcher(url == null ? "" : url);
    return region.find() ? region.group(1) : "us";
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

    Integer durationMs = item.get("trackTimeMillis") instanceof Number duration ? duration.intValue() : null;
    LocalDate releaseDate = parseReleaseDate((String) item.get("releaseDate"));
    PlatformTrack candidate = new PlatformTrack(track, platform, trackId, cleanAppleUrl(url), title, artist);
    if (!isVerifiedRecording(track, candidate, durationMs, releaseDate)) {
      log.warn("[Apple] ISRC 결과 근거 부족 skip: '{}' vs '{}' (ISRC={})", track.getTitle(), title, isrc);
      return null;
    }

    return candidate;
  }

  private PlatformTrack searchAppleByTitleArtist(Track track, Platform platform) {
    if (track.getTitle() == null || track.getArtist() == null) return null;

    for (String mainArtist : TrackMatchVerifier.artistNames(track.getArtist())) {
      for (String cleanTitle : TrackMatchVerifier.names(track.getTitle())) {
        PlatformTrack result = searchAppleQuery(track, platform, cleanTitle, mainArtist);
        if (result != null) return result;
      }
    }
    for (String cleanTitle : TrackMatchVerifier.names(track.getTitle())) {
      PlatformTrack result = searchAppleQuery(track, platform, cleanTitle, null);
      if (result != null) return result;
    }
    log.warn("[Apple] 매칭 실패 - title: '{}', artist: '{}'", track.getTitle(), track.getArtist());
    return null;
  }

  private PlatformTrack searchAppleQuery(Track track, Platform platform, String cleanTitle, String mainArtist) {
    String query = mainArtist == null ? cleanTitle : cleanTitle + " " + mainArtist;
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    log.info("[Apple] title+artist 검색 시작 - query: '{}', title: '{}', mainArtist: '{}'",
        query, track.getTitle(), mainArtist);

    // KR → US → JP 순서로 시도 (한국/글로벌 음원 우선, JP는 일본 전용 음원 폴백)
    for (String country : new String[]{"kr", "us", "jp"}) {
      String searchUrl = "https://itunes.apple.com/search?term=" + encoded
          + "&entity=song&limit=15&country=" + country;

      Map response = getAppleResponseAsMap(searchUrl, "Failed to parse Apple search response");
      List results = (List) response.get("results");
      log.info("[Apple] country={} resultCount={}", country, results == null ? 0 : results.size());
      if (results == null || results.isEmpty()) continue;

      for (Object obj : results) {
        Map item = (Map) obj;
        String resultTitle = (String) item.get("trackName");
        String resultArtist = (String) item.get("artistName");
        log.info("[Apple] 후보: title='{}' artist='{}'", resultTitle, resultArtist);

        Integer resultDuration = item.get("trackTimeMillis") instanceof Number duration
            ? duration.intValue() : null;
        LocalDate resultReleaseDate = parseReleaseDate((String) item.get("releaseDate"));
        Object rawTrackId = item.get("trackId");
        String trackId = rawTrackId != null ? String.valueOf(rawTrackId) : null;
        String url = (String) item.get("trackViewUrl");
        if (trackId == null || url == null) continue;

        String verifiedUrl = cleanAppleUrl(url);
        PlatformTrack candidate = new PlatformTrack(track, platform, trackId, verifiedUrl, resultTitle, resultArtist);
        if (!isVerifiedRecording(track, candidate, resultDuration, resultReleaseDate)) continue;
        log.info("[Apple] 매칭 성공: '{}' - '{}'", resultTitle, verifiedUrl);
        return candidate;
      }
    }
    return null;
  }

  private LocalDate parseReleaseDate(String value) {
    return value != null && value.length() >= 10 ? LocalDate.parse(value.substring(0, 10)) : null;
  }

  private boolean isVerifiedRecording(Track source, PlatformTrack candidate, Integer duration, LocalDate released) {
    if (!TrackMatchVerifier.isEvidenceMatch(source, candidate.getTitle(), candidate.getArtist(), duration, released)) return false;
    if (TrackMatchVerifier.hasCompatibleReleaseDate(source.getReleaseDate(), released)) return true;
    // English storefronts can give different language recordings the same title.
    PlatformTrack localized = preferKoreanStorefront(candidate);
    return "kr".equals(storefront(localized.getUrl()))
        && TrackMatchVerifier.isEvidenceMatch(source, localized.getTitle(), localized.getArtist(), duration, released);
  }

  private String cleanAppleUrl(String url) {
    if (url == null) return null;
    return url.replaceAll("\\?uo=\\d+&", "?")   // Preserve the storefront where the song was found.
              .replaceAll("[?&]uo=\\d+", "")     // uo= 마지막 또는 유일한 파라미터인 경우
              .replaceAll("\\?$", "");
  }

  private Map getAppleResponseAsMap(String url, String errorMessage) {
    try {
      String responseBody = webClient
              .get()
              .uri(java.net.URI.create(url))
              .retrieve()
              .bodyToMono(String.class)
              .block(java.time.Duration.ofSeconds(10));

      return objectMapper.readValue(responseBody, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(errorMessage + ": " + e.getMessage(), e);
    }
  }
}
