package com.playona.api.domain.platform.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlatformRepository extends JpaRepository<Platform, Integer> {
  List<Platform> findByIsActiveTrue();
}