package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class YoutubeTrackService {

  @Value("${youtube.api-key}")
  private String apiKey;

  private final TrackRepository trackRepository;
  private final WebClient webClient = WebClient.create("https://www.googleapis.com");

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Track getTrackFromUrl(String url) {
    String videoId = extractVideoId(url);

    if (videoId.length() != 11) {
      throw new RuntimeException("Invalid YouTube video ID: " + videoId);
    }

    Map response = webClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/youtube/v3/videos")
                    .queryParam("part", "snippet,contentDetails")
                    .queryParam("id", videoId)
                    .queryParam("key", apiKey)
                    .build())
            .retrieve()
            .bodyToMono(Map.class)
            .block();

    if (response == null) {
      throw new RuntimeException("No response from YouTube API");
    }

    List items = (List) response.get("items");
    if (items == null || items.isEmpty()) {
      throw new RuntimeException("No YouTube video found for ID: " + videoId);
    }

    Map firstItem = (Map) items.get(0);
    Map snippet = (Map) firstItem.get("snippet");
    Map contentDetails = (Map) firstItem.get("contentDetails");

    String rawTitle = snippet != null ? (String) snippet.get("title") : null;
    String artist = snippet != null ? (String) snippet.get("channelTitle") : null;
    String title = cleanTitle(rawTitle, artist);

    String thumbnail = null;
    if (snippet != null) {
      Map thumbnails = (Map) snippet.get("thumbnails");
      if (thumbnails != null) {
        Map high = (Map) thumbnails.get("high");
        if (high != null) {
          thumbnail = (String) high.get("url");
        }
      }
    }

    String youtubeUrl = "https://music.youtube.com/watch?v=" + videoId;

    Track existingTrack = trackRepository.findFirstBySourceUrl(youtubeUrl).orElse(null);
    if (existingTrack != null) {
      return existingTrack;
    }

    Track newTrack = new Track(title, artist, thumbnail, youtubeUrl);

    if (contentDetails != null) {
      String duration = (String) contentDetails.get("duration");
      if (duration != null) {
        newTrack.setDurationMs(parseIsoDurationToMillis(duration));
      }
    }

    if (snippet != null) {
      String publishedAt = (String) snippet.get("publishedAt");
      if (publishedAt != null && publishedAt.length() >= 10) {
        newTrack.setReleaseDate(LocalDate.parse(publishedAt.substring(0, 10)));
      }
    }

    newTrack.setAlbum(null);

    return trackRepository.save(newTrack);
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    String query = track.getTitle() + " " + track.getArtist();

    Map response = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/youtube/v3/search")
            .queryParam("part", "snippet")
            .queryParam("q", query)
            .queryParam("type", "video")
            .queryParam("videoCategoryId", "10")
            .queryParam("maxResults", "1")
            .queryParam("key", apiKey)
            .build())
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    if (response == null) return null;
    List items = (List) response.get("items");
    if (items == null || items.isEmpty()) return null;

    Map item = (Map) items.get(0);
    Map idMap = (Map) item.get("id");
    if (idMap == null) return null;
    String videoId = (String) idMap.get("videoId");
    if (videoId == null) return null;

    Map snippet = (Map) item.get("snippet");
    if (snippet == null) return null;
    String url = "https://music.youtube.com/watch?v=" + videoId;
    String title = (String) snippet.get("title");
    String artist = (String) snippet.get("channelTitle");

    return new PlatformTrack(track, platform, videoId, url, title, artist);
  }

  /**
   * YouTube 영상 제목에서 실제 곡명 추출.
   * 예) "Mrs. GREEN APPLE「lulu.」Official Music Video" → "lulu."
   */
  private String cleanTitle(String title, String artist) {
    if (title == null) return null;

    // 1. 일본어/한국어 꺾쇠 괄호 안 내용 추출 「lulu.」→ lulu.
    java.util.regex.Matcher bracketMatcher =
        java.util.regex.Pattern.compile("[「『【〔\\[]([^」』】〕\\]]+)[」』】〕\\]]").matcher(title);
    if (bracketMatcher.find()) {
      return bracketMatcher.group(1).trim();
    }

    // 2. 흔한 영상 키워드 제거 (대소문자 무시)
    String cleaned = title
        .replaceAll("(?i)\\s*[\\-|]?\\s*official\\s+music\\s+video\\s*", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*official\\s+video\\s*", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*official\\s+audio\\s*", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*music\\s+video\\s*", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*lyric(s)?\\s+(video\\s*)?", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*\\bMV\\b\\s*", " ")
        .replaceAll("(?i)\\s*[\\-|]?\\s*\\(live[^)]*\\)\\s*", " ")
        .trim();

    // 3. 제목 앞 아티스트명 중복 제거 ("Mrs. GREEN APPLE - lulu." → "lulu.")
    if (artist != null) {
      String artistLower = artist.toLowerCase().replaceAll("[^a-z0-9]", "");
      String cleanedLower = cleaned.toLowerCase().replaceAll("[^a-z0-9]", "");
      if (!artistLower.isEmpty() && cleanedLower.startsWith(artistLower)) {
        cleaned = cleaned.substring(artist.length())
            .replaceAll("^\\s*[-|:「]\\s*", "").trim();
      }
    }

    return cleaned.isEmpty() ? title : cleaned;
  }

  private String extractVideoId(String url) {
    if (url == null || url.isBlank()) {
      throw new RuntimeException("YouTube URL is empty");
    }

    if (url.contains("youtu.be/")) {
      return url.split("youtu.be/")[1].split("\\?")[0];
    }

    if (url.contains("youtube.com/watch?v=") || url.contains("music.youtube.com/watch?v=")) {
      return url.split("v=")[1].split("&")[0];
    }

    throw new RuntimeException("Not a valid YouTube URL: " + url);
  }

  private Integer parseIsoDurationToMillis(String isoDuration) {
    long millis = Duration.parse(isoDuration).toMillis();
    // Integer 범위 초과 시 (약 24.8일 이상) 최대값으로 cap
    return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
  }
}