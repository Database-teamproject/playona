package com.playona.api.domain.track.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracks")
@Getter
@NoArgsConstructor
public class Track {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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

  @Column(name = "source_url")
  private String sourceUrl;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
  // 기존 생성자 교체
  public Track(String title, String artist, String thumbnailUrl, String sourceUrl) {
    this.title = title;
    this.artist = artist;
    this.thumbnailUrl = thumbnailUrl;
    this.sourceUrl = sourceUrl;
  }
}
