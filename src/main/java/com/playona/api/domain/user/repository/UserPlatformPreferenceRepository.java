package com.playona.api.domain.user.repository;

import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserPlatformPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPlatformPreferenceRepository extends JpaRepository<UserPlatformPreference, Long> {
    List<UserPlatformPreference> findByUserOrderByPriorityAsc(User user);
    void deleteByUser(User user);
}