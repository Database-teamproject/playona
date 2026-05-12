package com.playona.api.domain.link.entity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.track.entity.Track;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {
  Optional<SharedLink> findByTrackAndUser(Track track, User user);
  // 비로그인용
  Optional<SharedLink> findByTrackAndUserIsNull(Track track);

  Optional<SharedLink> findByShortCode(String shortCode);
  List<SharedLink> findByUserOrderByCreatedAtDesc(User user);
  boolean existsByShortCode(String shortCode);
}