package com.playona.api.domain.platform.entity;

import com.playona.api.domain.track.entity.Track;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "platform_tracks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"track_id", "platform_id"})
)
@Getter
@NoArgsConstructor
public class PlatformTrack {

  public PlatformTrack(Track track, Platform platform, String platformTrackId, String url, String title, String artist) {
    this.track = track;
    this.platform = platform;
    this.platformTrackId = platformTrackId;
    this.url = url;
    this.title = title;
    this.artist = artist;
    this.fetchedAt = LocalDateTime.now();
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "track_id", nullable = false)
  private Track track;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "platform_id", nullable = false)
  private Platform platform;

  @Column(name = "platform_track_id")
  private String platformTrackId;

  private String url;
  private String title;
  private String artist;

  @Column(name = "fetched_at")
  private LocalDateTime fetchedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public boolean isSearchFallback() {
    return switch (platform.getSlug()) {
      case "flo", "genie", "melon" -> url == null || url.contains("/search");
      default -> false;
    };
  }

  public String getDisplayName() {
    if ("apple".equals(platform.getSlug()) && url != null) {
      var region = java.util.regex.Pattern.compile("^https://music\\.apple\\.com/([a-z]{2})/").matcher(url);
      if (region.find() && !"kr".equals(region.group(1))) {
        String country = switch (region.group(1)) {
          case "us" -> "미국";
          case "jp" -> "일본";
          default -> region.group(1).toUpperCase(java.util.Locale.ROOT);
        };
        return platform.getName() + " (" + country + " 스토어)";
      }
    }
    return platform.getName();
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (fetchedAt == null) {
      fetchedAt = LocalDateTime.now();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
