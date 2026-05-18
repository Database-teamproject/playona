package com.playona.api.domain.track.repository;

import com.playona.api.domain.track.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {
  boolean existsByIsrc(String isrc);
  Optional<Track> findByIsrc(String isrc);
  Optional<Track> findFirstBySourceUrl(String sourceUrl);
  Optional<Track> findByTrackUuid(String trackUuid);
}