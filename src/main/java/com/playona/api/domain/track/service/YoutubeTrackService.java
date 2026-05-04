package com.playona.api.domain.track.service;

import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class YoutubeTrackService {

  @Value("${youtube.api-key}")
  private String apiKey;

  private final TrackRepository trackRepository;
  private final WebClient webClient = WebClient.create("https://www.googleapis.com");

  public Track getTrackFromUrl(String url) {
    String videoId = extractVideoId(url);

    // 이미 DB에 있으면 그냥 반환 (중복 저장 방지)
    // 나중에 platform_track_id로 체크하도록 개선 예정

    // YouTube API 호출
    Map response = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/youtube/v3/videos")
            .queryParam("part", "snippet")
            .queryParam("id", videoId)
            .queryParam("key", apiKey)
            .build())
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    // 응답에서 곡 정보 추출
    var items = (java.util.List) response.get("items");
    if (items == null || items.isEmpty()) {
      throw new RuntimeException("YouTube에서 영상을 찾을 수 없습니다: " + videoId);
    }

    var snippet = (Map) ((Map) items.get(0)).get("snippet");
    String title = (String) snippet.get("title");
    String artist = (String) snippet.get("channelTitle");
    String thumbnail = (String) ((Map) ((Map) snippet.get("thumbnails")).get("high")).get("url");

    String youtubeUrl = "https://music.youtube.com/watch?v=" + videoId;
    Track track = new Track(title, artist, thumbnail, youtubeUrl);
    return trackRepository.save(track);
  }

  private String extractVideoId(String url) {
    // https://www.youtube.com/watch?v=VIDEO_ID
    // https://youtu.be/VIDEO_ID
    if (url.contains("youtu.be/")) {
      return url.split("youtu.be/")[1].split("\\?")[0];
    } else if (url.contains("v=")) {
      return url.split("v=")[1].split("&")[0];
    }
    throw new RuntimeException("올바른 YouTube URL이 아닙니다: " + url);
  }
}