package com.playona.api.domain.link.dto;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.platform.entity.PlatformTrack;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class LinkResponse {

  private final String shortCode;
  private final String trackTitle;
  private final String trackArtist;
  private final String thumbnailUrl;
  private final int clickCount;
  private final String shareUrl;
  private final List<Map<String, String>> platforms;

  public LinkResponse(SharedLink sharedLink, String baseUrl, List<PlatformTrack> platformTracks) {
    this.shortCode = sharedLink.getShortCode();
    this.trackTitle = sharedLink.getTrack().getTitle();
    this.trackArtist = sharedLink.getTrack().getArtist();
    this.thumbnailUrl = sharedLink.getTrack().getThumbnailUrl() != null
        ? sharedLink.getTrack().getThumbnailUrl() : "";
    this.clickCount = sharedLink.getClickCount() != null
        ? sharedLink.getClickCount() : 0;
    this.shareUrl = baseUrl + "/t/" + sharedLink.getShortCode();
    this.platforms = platformTracks.stream()
        .map(pt -> Map.of(
            "slug", pt.getPlatform().getSlug(),
            "name", pt.getPlatform().getName(),
            "url", pt.getUrl()
        ))
        .toList();
  }
}