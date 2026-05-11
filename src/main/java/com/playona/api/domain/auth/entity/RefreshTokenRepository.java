package com.playona.api.domain.auth.entity;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  @Transactional
  @Modifying
  void deleteByUserId(Long userId);
}