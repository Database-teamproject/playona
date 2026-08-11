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

import java.net.URLEncoder;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    Map localized = snippet != null ? (Map) snippet.get("localized") : null;
    String localizedTitle = localized != null ? (String) localized.get("title") : null;
    String rawTitle = (localizedTitle != null && !localizedTitle.isBlank()) ? localizedTitle : (snippet != null ? (String) snippet.get("title") : null);
    // "Square's dream (네모의 꿈)" → "네모의 꿈" (괄호 안 한국어가 있으면 추출)
    rawTitle = extractKoreanIfPresent(rawTitle);
    String rawArtist = snippet != null ? (String) snippet.get("channelTitle") : null;
    String artist = cleanArtist(rawArtist);
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
    String mainArtist = cleanArtistForSearch(
        track.getArtist() != null ? track.getArtist().split(",")[0].trim() : ""
    );
    String queryRaw = normalizeQuery(track.getTitle()) + " " + normalizeQuery(mainArtist);
    String queryEncoded = URLEncoder.encode(queryRaw, java.nio.charset.StandardCharsets.UTF_8)
        .replace("+", "%20");

    // Topic 채널(자동 생성 공식 음원) 우선 검색 → 직접 재생 URL
    String directUrl = findTopicChannelVideoUrl(queryRaw, track, mainArtist);
    if (directUrl != null) {
      return new PlatformTrack(track, platform, null, directUrl, track.getTitle(), track.getArtist());
    }

    // fallback: 검색 URL
    String searchUrl = "https://music.youtube.com/search?q=" + queryEncoded;
    return new PlatformTrack(track, platform, null, searchUrl, track.getTitle(), track.getArtist());
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
        boolean trustedChannel = channelTitle != null
            && (channelTitle.endsWith("- Topic") || isOfficialChannel(artist, channelTitle));
        if (!trustedChannel || isNoiseVideo(videoTitle) || !isTitleClean(track.getTitle(), artist, videoTitle)) {
          continue;
        }

        Long duration = durations.get(videoId);
        if (!hasCompatibleDuration(track.getDurationMs(), duration)) continue;

        int score = channelTitle.endsWith("- Topic") ? 2 : 0;
        if (isOfficialChannel(artist, channelTitle)) score += 1;
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

  /**
   * 제목이 곡명+아티스트와 거의 일치하는지 판단 (순수 음원 양성 탐지).
   * 곡명과 아티스트명을 제거하고 남은 텍스트가 짧으면 방송/쇼 영상이 아닌 음원으로 간주.
   */
  static boolean isTitleClean(String trackTitle, String artist, String videoTitle) {
    if (trackTitle == null || videoTitle == null) return false;
    String norm = normalizeForComparison(videoTitle);
    String normTitle = normalizeForComparison(trackTitle);
    if (normTitle.isEmpty() || !norm.contains(normTitle)) return false;
    String remaining = norm.replace(normTitle, "");
    if (artist != null) {
      String normArtist = normalizeForComparison(artist.split("[,&]")[0]);
      remaining = remaining.replace(normArtist, "");
    }
    // 남은 텍스트 15자 이하 = 곡명+아티스트 외 추가 정보 없음
    return remaining.replaceAll("\\s+", " ").trim().length() <= 15;
  }

  static boolean isAcceptedSourceVideo(String categoryId, String liveBroadcastContent) {
    return ("10".equals(categoryId) || "24".equals(categoryId))
        && (liveBroadcastContent == null || "none".equals(liveBroadcastContent));
  }

  static boolean hasCompatibleDuration(Integer trackDurationMs, Long candidateDurationMs) {
    return trackDurationMs == null || candidateDurationMs == null
        || Math.abs(trackDurationMs.longValue() - candidateDurationMs) <= 15_000L;
  }

  private static String normalizeForComparison(String value) {
    return value.toLowerCase()
        .replaceAll("[^a-z0-9가-힣\\u3040-\\u30ff\\u4e00-\\u9fff ]", " ")
        .replaceAll("\\s+", " ").trim();
  }

  /** 가사/라이브/커버/MV 등 노이즈 영상 여부 판단 */
  private boolean isNoiseVideo(String title) {
    if (title == null) return false;
    String lower = title.toLowerCase();
    return lower.contains("live") || lower.contains("concert") || lower.contains("tour")
        || lower.contains("라이브") || lower.contains("공연") || lower.contains("콘서트")
        || lower.contains("가사") || lower.contains("lyrics") || lower.contains("lyric")
        || lower.contains("cover") || lower.contains("커버") || lower.contains("reaction")
        || lower.contains("music video") || lower.contains("뮤직비디오") || lower.contains("뮤비")
        || lower.contains("노래방") || lower.contains("karaoke") || lower.contains("반주")
        || lower.contains("레전드") || lower.contains("모음") || lower.contains("직캠")
        || lower.contains("fancam") || lower.contains("소름") || lower.contains("remix")
        || lower.matches(".*\\bmr\\b.*")
        || lower.matches(".*\\bmv\\b.*")
        || lower.contains("무대") || lower.contains("stage")
        || lower.contains("스케치북") || lower.contains("뮤직뱅크") || lower.contains("인기가요")
        || lower.contains("쇼챔피언") || lower.contains("엠카운트다운") || lower.contains("뮤직쇼")
        || lower.matches(".*\\[.*\\d{4}.*\\].*")  // [2015.12.11] 형태 날짜
        || lower.matches(".*@.*");                  // @방송프로그램 형태
  }

    private static String normalizeQuery(String s) {
    if (s == null) return "";
    return s.replaceAll("[\u2018\u2019\u02bc\u00b4`]", "'");
  }

  private String cleanArtistForSearch(String artist) {
    if (artist == null || artist.isBlank()) return "";
    return artist.replaceAll("\\s*[\\(\\[].*?[\\)\\]]\\s*", " ").replaceAll("\\s+", " ").trim();
  }

  /** 아티스트명과 채널명 유사도 체크 (공식 채널 판별) */
  private boolean isOfficialChannel(String artist, String channelTitle) {
    if (artist == null || channelTitle == null) return false;
    String na = artist.toLowerCase().replaceAll("[^a-z0-9가-힣]", "");
    String nc = channelTitle.toLowerCase().replaceAll("[^a-z0-9가-힣]", "");
    if (na.isEmpty() || nc.isEmpty()) return false;
    return na.equals(nc) || nc.contains(na) || na.contains(nc);
  }

  /** YouTube 채널명에서 아티스트명 추출. "Mrs. GREEN APPLE - Topic" → "Mrs. GREEN APPLE" */
  private String cleanArtist(String channelTitle) {
    if (channelTitle == null) return null;
    return channelTitle.replaceAll("\\s*-\\s*Topic$", "").trim();
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
        .replaceAll("(?i)\\s*\\(live[^)]*\\)\\s*", " ")
        .replaceAll("(?i)\\s*\\([^)]*ver\\.?[^)]*\\)\\s*", " ")   // (Hyperpop ver.) 등
        .replaceAll("(?i)\\s*\\([^)]*버전[^)]*\\)\\s*", " ")       // (하이퍼팝 버전) 등
        .replaceAll("\\s*\\([가-힣]+\\)\\s*", " ")                  // (몰리얌) 같은 한글 괄호
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

  private String extractKoreanIfPresent(String title) {
    if (title == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("[\\(（]([^\\)）]*[가-힣][^\\)）]*)[\\)）]")
        .matcher(title);
    if (m.find()) return m.group(1).trim();
    return title;
  }

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
