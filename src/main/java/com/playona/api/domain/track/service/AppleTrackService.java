package com.playona.api.domain.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    String sourceUrl = (String) item.get("trackViewUrl");
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

  private String extractTrackId(String url) {
    if (url == null || !url.contains("music.apple.com/")) {
      throw new IllegalArgumentException("Not a valid Apple Music URL: " + url);
    }

    // album URL with selected song: ?i=1337452977
    int index = url.indexOf("?i=");
    if (index != -1) {
      String songId = url.substring(index + 3).split("&")[0];
      if (songId.matches("\\d+")) {
        return songId;
      }
    }

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
      // 1. ISRC first
      if (track.getIsrc() != null && !track.getIsrc().isBlank()) {
        PlatformTrack byIsrc = searchAppleByIsrc(track, platform, track.getIsrc());
        if (byIsrc != null) {
          return byIsrc;
        }
      }

      // 2. fallback: title + artist
      return searchAppleByTitleArtist(track, platform);

    } catch (Exception e) {
      throw new RuntimeException("Apple search failed: " + e.getMessage(), e);
    }
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

    return new PlatformTrack(track, platform, trackId, url, title, artist);
  }

  private PlatformTrack searchAppleByTitleArtist(Track track, Platform platform) {
    String query = track.getTitle() + " " + track.getArtist();

    String searchUrl = "https://itunes.apple.com/search?term=" +
            URLEncoder.encode(query, StandardCharsets.UTF_8) +
            "&entity=song&limit=1";

    Map response = getAppleResponseAsMap(searchUrl, "Failed to parse Apple search response");

    List results = (List) response.get("results");
    if (results == null || results.isEmpty()) return null;

    Map item = (Map) results.get(0);

    Object rawTrackId = item.get("trackId");
    String trackId = rawTrackId != null ? String.valueOf(rawTrackId) : null;
    String url = (String) item.get("trackViewUrl");
    String title = (String) item.get("trackName");
    String artist = (String) item.get("artistName");

    if (trackId == null || url == null) return null;

    // 검색 결과가 원곡과 너무 다르면 매칭 거부 (오매칭 방지)
    if (!isSimilarEnough(track.getTitle(), title)) return null;

    return new PlatformTrack(track, platform, trackId, url, title, artist);
  }

  /**
   * 두 제목의 유사도를 간단히 검사: 한쪽이 다른 쪽을 포함하거나 첫 단어가 일치하면 OK.
   */
  private boolean isSimilarEnough(String original, String result) {
    if (original == null || result == null) return false;
    String a = original.toLowerCase().replaceAll("[^a-z0-9가-힣]", "");
    String b = result.toLowerCase().replaceAll("[^a-z0-9가-힣]", "");
    if (a.isEmpty() || b.isEmpty()) return false;
    // 한쪽이 다른 쪽을 포함하면 OK
    if (a.contains(b) || b.contains(a)) return true;
    // 앞 4글자 이상 일치하면 OK
    int common = Math.min(Math.min(a.length(), b.length()), 4);
    return a.substring(0, common).equals(b.substring(0, common));
  }

  private Map getAppleResponseAsMap(String url, String errorMessage) {
    try {
      String responseBody = WebClient.create()
              .get()
              .uri(url)
              .retrieve()
              .bodyToMono(String.class)
              .block();

      return objectMapper.readValue(responseBody, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(errorMessage + ": " + e.getMessage(), e);
    }
  }
}