package com.playona.api.domain.user.repository;

import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserPlatformPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPlatformPreferenceRepository extends JpaRepository<UserPlatformPreference, Long> {

    List<UserPlatformPreference> findByUserOrderByPriorityAsc(User user);

    // derived delete는 엔티티를 먼저 SELECT 후 remove하므로 flush 순서 보장이 안 됨.
    // JPQL bulk delete로 교체 + clearAutomatically로 PC를 즉시 정리
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserPlatformPreference p WHERE p.user = :user")
    void deleteByUser(@Param("user") User user);
}
