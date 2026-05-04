package com.playona.api.domain.link.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {

  Optional<SharedLink> findByShortCode(String shortCode);

  boolean existsByShortCode(String shortCode);
}