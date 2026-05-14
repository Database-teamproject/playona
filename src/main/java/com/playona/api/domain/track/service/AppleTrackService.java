package com.playona.api.domain.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppleTrackService {

  private final TrackRepository trackRepository;
  private final WebClient webClient = WebClient.create("https://itunes.apple.com");
  private final ObjectMapper objectMapper = new ObjectMapper();

  public Track getTrackFromUrl(String url) {
    String trackId = extractAppleTrackId(url);

    try {
      String responseBody = webClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/lookup")
              .queryParam("id", trackId)
              .queryParam("entity", "song")
              .build())
          .header("Accept", "application/json")
          .retrieve()
          .bodyToMono(String.class)
          .block();

      Map response = objectMapper.readValue(responseBody, Map.class);
      List results = (List) response.get("results");
      if (results == null || results.isEmpty()) {
        throw new RuntimeException("Apple Music에서 트랙을 찾을 수 없습니다: " + trackId);
      }

      Map item = (Map) results.get(0);
      // wrapperType이 track인 경우만 처리 (artist, collection 등 제외)
      if (!"track".equals(item.get("wrapperType"))) {
        throw new RuntimeException("해당 Apple Music ID는 트랙이 아닙니다: " + trackId);
      }

      String title = (String) item.get("trackName");
      String artist = (String) item.get("artistName");
      String thumbnail = ((String) item.get("artworkUrl100")).replace("100x100", "500x500");
      String sourceUrl = (String) item.get("trackViewUrl");
      // 쿼리 파라미터 제거해서 정규화
      String cleanUrl = sourceUrl.split("\\?")[0];

      // source_url 중복 체크
      Track existing = trackRepository.findBySourceUrl(cleanUrl);
      if (existing != null) return existing;

      Track track = new Track(title, artist, thumbnail, cleanUrl);
      return trackRepository.save(track);

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Apple Music 트랙 조회 실패: " + e.getMessage());
    }
  }

  private String extractAppleTrackId(String url) {
    // https://music.apple.com/kr/album/album-name/123456789?i=987654321
    if (url.contains("?i=")) {
      return url.split("\\?i=")[1].split("&")[0];
    }
    // https://music.apple.com/kr/song/song-name/987654321
    String path = url.split("\\?")[0];
    String[] segments = path.split("/");
    String lastSegment = segments[segments.length - 1];
    if (lastSegment.matches("\\d+")) {
      return lastSegment;
    }
    throw new IllegalArgumentException("올바른 Apple Music URL이 아닙니다: " + url);
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    try {
      // 1차: ISRC lookup
      if (track.getIsrc() != null) {
        PlatformTrack result = lookupByIsrc(track, platform);
        if (result != null) return result;
      }

      // 2차: 제목+아티스트 fallback
      return searchByQuery(track.getTitle() + " " + track.getArtist(), track, platform);

    } catch (Exception e) {
      throw new RuntimeException("Apple Music 검색 실패: " + e.getMessage());
    }
  }

  private PlatformTrack lookupByIsrc(Track track, Platform platform) throws Exception {
    String responseBody = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/lookup")
            .queryParam("isrc", track.getIsrc())
            .build())
        .header("Accept", "application/json")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    return parseResponse(responseBody, track, platform);
  }

  private PlatformTrack searchByQuery(String query, Track track, Platform platform) throws Exception {
    String responseBody = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/search")
            .queryParam("term", query)
            .queryParam("media", "music")
            .queryParam("entity", "song")
            .queryParam("country", "kr")
            .queryParam("limit", "1")
            .build())
        .header("Accept", "application/json")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    return parseResponse(responseBody, track, platform);
  }

  private PlatformTrack parseResponse(String responseBody, Track track, Platform platform) throws Exception {
    Map response = objectMapper.readValue(responseBody, Map.class);
    List results = (List) response.get("results");
    if (results == null || results.isEmpty()) return null;

    Map item = (Map) results.get(0);
    String url = (String) item.get("trackViewUrl");
    String title = (String) item.get("trackName");
    String artist = (String) item.get("artistName");
    String trackId = String.valueOf(item.get("trackId"));

    return new PlatformTrack(track, platform, trackId, url, title, artist);
  }
}