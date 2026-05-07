package com.playona.api.domain.link.entity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.playona.api.domain.user.entity.User;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {

  Optional<SharedLink> findByShortCode(String shortCode);

  boolean existsByShortCode(String shortCode);

  List<SharedLink> findByUserOrderByCreatedAtDesc(User user);
}