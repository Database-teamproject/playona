package com.playona.api.domain.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppleTrackService {

  private final WebClient webClient = WebClient.create("https://itunes.apple.com");
  private final ObjectMapper objectMapper = new ObjectMapper();

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