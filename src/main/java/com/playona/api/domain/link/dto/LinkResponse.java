package com.playona.api.domain.link.dto;

import com.playona.api.domain.link.entity.SharedLink;
import lombok.Getter;

@Getter
public class LinkResponse {

  private final String shortCode;
  private final String trackTitle;
  private final String trackArtist;
  private final String thumbnailUrl;
  private final int clickCount;
  private final String shareUrl;

  public LinkResponse(SharedLink sharedLink, String baseUrl) {
    this.shortCode = sharedLink.getShortCode();
    this.trackTitle = sharedLink.getTrack().getTitle();
    this.trackArtist = sharedLink.getTrack().getArtist();
    this.thumbnailUrl = sharedLink.getTrack().getThumbnailUrl() != null
        ? sharedLink.getTrack().getThumbnailUrl() : "";
    this.clickCount = sharedLink.getClickCount() != null
        ? sharedLink.getClickCount() : 0;
    this.shareUrl = baseUrl + "/t/" + sharedLink.getShortCode();
  }
}