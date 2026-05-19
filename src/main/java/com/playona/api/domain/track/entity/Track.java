package com.playona.api.domain.track.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
public class Track {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "track_uuid", unique = true, nullable = true)
  private String trackUuid;

  @Column(nullable = true)
  private String isrc;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String artist;

  private String album;

  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "thumbnail_url")
  private String thumbnailUrl;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "source_url", unique = true)
  private String sourceUrl;

  public Track(String title, String artist, String thumbnailUrl, String sourceUrl) {
    this.title = title;
    this.artist = artist;
    this.thumbnailUrl = thumbnailUrl;
    this.sourceUrl = sourceUrl;
  }

  public Track(String title, String artist, String thumbnailUrl, String sourceUrl, String isrc) {
    this.title = title;
    this.artist = artist;
    this.thumbnailUrl = thumbnailUrl;
    this.sourceUrl = sourceUrl;
    this.isrc = isrc;
  }

  @PrePersist
  protected void onCreate() {
    if (trackUuid == null) {
      trackUuid = UUID.randomUUID().toString();
    }
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}