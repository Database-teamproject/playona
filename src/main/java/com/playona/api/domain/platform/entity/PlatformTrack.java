package com.playona.api.domain.platform.entity;

import com.playona.api.domain.track.entity.Track;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_tracks",
    uniqueConstraints = @UniqueConstraint(columnNames = {"track_id", "platform_id"}))
@Getter
@NoArgsConstructor
public class PlatformTrack {

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

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}