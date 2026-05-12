package com.playona.api.domain.track.entity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {

  boolean existsByIsrc(String isrc);

  Optional<Track> findFirstByIsrc(String isrc);
}