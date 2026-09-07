package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
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
                    .queryParam("part", "snippet,contentDetails,localizations")
                    .queryParam("id", videoId)
                    .queryParam("hl", "ko")
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

    String categoryId = snippet != null ? String.valueOf(snippet.get("categoryId")) : null;
    String liveBroadcastContent = snippet != null ? (String) snippet.get("liveBroadcastContent") : null;
    if (!isAcceptedSourceVideo(categoryId, liveBroadcastContent)) {
      throw new IllegalArgumentException("음악 또는 공식 뮤직비디오 URL만 지원합니다.");
    }

    String rawTitle = snippet != null ? (String) snippet.get("title") : null;
    String description = snippet != null ? (String) snippet.get("description") : null;
    if (isMusicCompilation(rawTitle, description)) {
      throw new IllegalArgumentException("해당 링크는 음악 모음 영상이므로 통합 링크를 만들 수 없습니다.");
    }
    SourceMetadata metadata = extractSourceMetadata(rawTitle,
        snippet != null ? (String) snippet.get("channelTitle") : null);
    if (metadata == null || isUnsupportedSourceVideo(rawTitle)) {
      throw new IllegalArgumentException("공식 음원 또는 뮤직비디오 링크만 통합 링크로 만들 수 있습니다.");
    }

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
    Track track = existingTrack != null ? existingTrack
        : new Track(metadata.title(), metadata.artist(), thumbnail, youtubeUrl);
    track.setTitle(metadata.title());
    track.setArtist(metadata.artist());
    track.setThumbnailUrl(thumbnail);

    if (contentDetails != null) {
      String duration = (String) contentDetails.get("duration");
      if (duration != null) {
        track.setDurationMs(parseIsoDurationToMillis(duration));
      }
    }

    if (snippet != null) {
      String publishedAt = (String) snippet.get("publishedAt");
      if (publishedAt != null && publishedAt.length() >= 10) {
        track.setReleaseDate(LocalDate.parse(publishedAt.substring(0, 10)));
      }
    }

    track.setAlbum(null);
    trackRepository.save(track);
    return track;
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    String mainArtist = track.getArtist() != null ? track.getArtist().split(",")[0].trim() : "";
    String queryRaw = track.getTitle() + " " + mainArtist;

    // Topic 채널(자동 생성 공식 음원) 우선 검색 → 직접 재생 URL
    String directUrl = findTopicChannelVideoUrl(queryRaw, track, mainArtist);
    if (directUrl != null) {
      return new PlatformTrack(track, platform, null, directUrl, track.getTitle(), track.getArtist());
    }

    return null;
  }

  /** YouTube Data API로 Topic 채널 영상 검색 → music.youtube.com 직접 재생 URL 반환 */
  private String findTopicChannelVideoUrl(String query, Track track, String artist) {
    try {
      Map<String, Map> candidates = new LinkedHashMap<>();
      collectCandidates(candidates, query + " topic", 5);
      collectCandidates(candidates, query, 10);

      if (candidates.isEmpty()) {
        log.warn("[YTMusic] 검색 결과 없음 - query: '{}'", query);
        return null;
      }

      Map<String, Long> durations = fetchVideosDuration(new ArrayList<>(candidates.keySet()));
      String bestVideoId = null;
      int bestScore = Integer.MIN_VALUE;

      for (Map.Entry<String, Map> entry : candidates.entrySet()) {
        String videoId = entry.getKey();
        Map snippet = entry.getValue();
        String channelTitle = (String) snippet.get("channelTitle");
        String videoTitle = (String) snippet.get("title");
        String candidateArtist = cleanArtist(channelTitle);
        if (!isOfficialChannel(artist, channelTitle) || isNoiseVideo(videoTitle)
            || !TrackMatchVerifier.hasMatchingTitleAndArtist(
                track.getTitle(), artist, videoTitle, candidateArtist)) {
          continue;
        }

        Long duration = durations.get(videoId);
        if (!hasCompatibleDuration(track.getDurationMs(), duration)) continue;

        int score = channelTitle.endsWith("- Topic") ? 1 : 0;
        if (duration != null && track.getDurationMs() != null) score += 1;
        if (score > bestScore) {
          bestScore = score;
          bestVideoId = videoId;
        }
      }

      if (bestVideoId == null) {
        log.warn("[YTMusic] 검증된 직접 매칭 없음 - query: '{}'", query);
        return null;
      }

      log.info("[YTMusic] 검증된 직접 매칭: url=watch?v={}", bestVideoId);
      return "https://music.youtube.com/watch?v=" + bestVideoId;
    } catch (Exception e) {
      log.warn("[YTMusic] API 오류: {}", e.getMessage());
      return null;
    }
  }

  private void collectCandidates(Map<String, Map> candidates, String query, int maxResults) {
    Map response = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/youtube/v3/search")
            .queryParam("part", "snippet")
            .queryParam("q", query)
            .queryParam("type", "video")
            .queryParam("maxResults", maxResults)
            .queryParam("key", apiKey)
            .build())
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    if (response == null) return;

    List items = (List) response.get("items");
    if (items == null) return;
    for (Object obj : items) {
      Map item = (Map) obj;
      Map id = (Map) item.get("id");
      Map snippet = (Map) item.get("snippet");
      String videoId = id != null ? (String) id.get("videoId") : null;
      if (videoId != null && snippet != null) candidates.putIfAbsent(videoId, snippet);
    }
  }

  /** 검색 결과 videoId 목록으로 duration(ms) 배치 조회 */
  private Map<String, Long> fetchVideosDuration(List<String> videoIds) {
    if (videoIds.isEmpty()) return Map.of();
    String ids = String.join(",", videoIds);

    Map response = webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/youtube/v3/videos")
            .queryParam("part", "contentDetails")
            .queryParam("id", ids)
            .queryParam("key", apiKey)
            .build())
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    if (response == null) return Map.of();
    List items = (List) response.get("items");
    if (items == null) return Map.of();

    Map<String, Long> result = new HashMap<>();
    for (Object obj : items) {
      Map item = (Map) obj;
      String id = (String) item.get("id");
      Map contentDetails = (Map) item.get("contentDetails");
      if (id == null || contentDetails == null) continue;
      String duration = (String) contentDetails.get("duration");
      if (duration != null) {
        try {
          result.put(id, Duration.parse(duration).toMillis());
        } catch (Exception ignored) {}
      }
    }
    return result;
  }

  static boolean isTitleClean(String trackTitle, String artist, String videoTitle) {
    return TrackMatchVerifier.hasMatchingTitleAndArtist(trackTitle, artist, videoTitle, artist);
  }

  static boolean isAcceptedSourceVideo(String categoryId, String liveBroadcastContent) {
    return ("10".equals(categoryId) || "24".equals(categoryId))
        && (liveBroadcastContent == null || "none".equals(liveBroadcastContent));
  }

  static boolean isMusicCompilation(String title, String description) {
    String normalizedTitle = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC);
    if (Pattern.compile("(?iu)\\b(playlist|compilation|medley|full\\s+album|music\\s+mix)\\b|플레이리스트|모음|메들리")
        .matcher(normalizedTitle).find()) return true;
    if (description == null) return false;

    boolean hasTrackList = Pattern.compile("(?iu)\\btrack\\s*list\\b|트랙\\s*리스트|수록곡")
        .matcher(description).find();
    var timestamps = new HashSet<String>();
    var songs = new HashSet<String>();
    var entries = Pattern.compile("(?m)^\\h*(\\d{1,3}:[0-5]\\d(?::[0-5]\\d)?)\\h+([^\\r\\n]+)")
        .matcher(description);
    // ponytail: 명시적인 모음 제목/트랙 목록만 판별. 메타데이터에 단서가 없는 모음은 별도 음원 식별이 필요하다.
    while (entries.find()) {
      String label = entries.group(2).trim();
      if (hasTrackList || Pattern.compile("\\h+[-–—|]\\h+").matcher(label).find()) {
        timestamps.add(entries.group(1));
        songs.add(label);
      }
    }
    return timestamps.size() >= 2 && songs.size() >= 2;
  }

  static boolean hasCompatibleDuration(Integer trackDurationMs, Long candidateDurationMs) {
    return trackDurationMs != null && candidateDurationMs != null
        && Math.abs(trackDurationMs.longValue() - candidateDurationMs) <= 30_000L;
  }

  /** 가사/라이브/커버/MV 등 노이즈 영상 여부 판단 */
  private boolean isNoiseVideo(String title) {
    if (title == null) return false;
    String lower = title.toLowerCase();
    return Pattern.compile("(?iu)\\b(live|concert|tour|lyrics?|cover|reaction|karaoke|remix|fancam)\\b|라이브|공연|콘서트|가사|커버|리액션|노래방|반주|모음|직캠")
        .matcher(title).find()
        || lower.matches(".*\\[.*\\d{4}.*\\].*");
  }

  private boolean isOfficialChannel(String artist, String channelTitle) {
    if (artist == null || channelTitle == null) return false;
    String na = normalizeIdentity(artist);
    String nc = normalizeIdentity(cleanArtist(channelTitle));
    if (na.isEmpty() || nc.isEmpty()) return false;
    return na.equals(nc);
  }

  /** YouTube 채널명에서 아티스트명 추출. "Mrs. GREEN APPLE - Topic" → "Mrs. GREEN APPLE" */
  private static String cleanArtist(String channelTitle) {
    if (channelTitle == null) return null;
    return channelTitle.replaceAll("\\s*-\\s*Topic$", "").trim();
  }

  static SourceMetadata extractSourceMetadata(String rawTitle, String channelTitle) {
    if (rawTitle == null || channelTitle == null) return null;
    String title = rawTitle.replaceFirst("(?iu)^\\s*\\[(?:official\\s+)?(?:mv|music\\s+video|audio)\\]\\s*", "")
        .replaceFirst("(?iu)\\s*[-|]?\\s*(official\\s+)?(?:music\\s+video|video|audio)\\s*$", "").trim();
    String channelArtist = cleanArtist(channelTitle);
    if (channelTitle.endsWith("- Topic") && !title.isBlank()) {
      return new SourceMetadata(removeArtistPrefix(title, channelArtist), channelArtist);
    }
    String[] parts = title.split("\\s+-\\s+", 2);
    if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
      return new SourceMetadata(parts[1].trim(), parts[0].trim());
    }
    return null;
  }

  private static boolean isUnsupportedSourceVideo(String title) {
    return title == null || Pattern.compile("(?iu)\\b(live|concert|tour|lyrics?|cover|reaction|karaoke|remix|fancam)\\b|라이브|공연|콘서트|가사|커버|리액션|노래방|반주|직캠")
        .matcher(title).find();
  }

  private static String removeArtistPrefix(String title, String artist) {
    String prefix = artist + " - ";
    return title.regionMatches(true, 0, prefix, 0, prefix.length()) ? title.substring(prefix.length()).trim() : title;
  }

  private static String normalizeIdentity(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase().replaceAll("[^a-z0-9가-힣\\u3040-\\u30ff\\u4e00-\\u9fff]", "");
  }

  record SourceMetadata(String title, String artist) {}

  static String extractVideoId(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("YouTube URL is empty");
    }

    var shortUrl = java.util.regex.Pattern
        .compile("^https?://youtu\\.be/([^?/#]+)")
        .matcher(url);
    if (shortUrl.find()) return shortUrl.group(1);

    var pathUrl = java.util.regex.Pattern
        .compile("^https?://(?:[\\w-]+\\.)?youtube\\.com/(?:shorts|live|embed)/([^?/#]+)")
        .matcher(url);
    if (pathUrl.find()) return pathUrl.group(1);

    var watchUrl = java.util.regex.Pattern
        .compile("^https?://(?:[\\w-]+\\.)?youtube\\.com/watch\\?(?:[^#]*&)?v=([^&#]+)")
        .matcher(url);
    if (watchUrl.find()) return watchUrl.group(1);

    throw new IllegalArgumentException("유튜브 개별 영상 URL을 입력해주세요. (재생목록, 채널 URL은 지원하지 않습니다)");
  }

  private Integer parseIsoDurationToMillis(String isoDuration) {
    long millis = Duration.parse(isoDuration).toMillis();
    // Integer 범위 초과 시 (약 24.8일 이상) 최대값으로 cap
    return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
  }
}
