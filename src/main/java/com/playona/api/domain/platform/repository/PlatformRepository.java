package com.playona.api.domain.platform.repository;

import com.playona.api.domain.platform.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Integer> {
    Optional<Platform> findBySlug(String slug);
    List<Platform> findByIsActiveTrue();
    List<Platform> findByIsActiveTrueOrderByIdAsc();
}