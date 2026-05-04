package com.playona.api.domain.track.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {

  boolean existsByIsrc(String isrc);

  Track findByIsrc(String isrc);
}