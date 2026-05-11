package com.playona.api.domain.platform.repository;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlatformTrackRepository extends JpaRepository<PlatformTrack, Long> {
  Optional<PlatformTrack> findByTrackAndPlatform(Track track, Platform platform);
  List<PlatformTrack> findByTrack(Track track);
}