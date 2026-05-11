package com.playona.api.domain.user.entity;

import com.playona.api.domain.platform.entity.Platform;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_platform_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_platform_preferences_user_platform", columnNames = {"user_id", "platform_id"}),
                @UniqueConstraint(name = "uk_user_platform_preferences_user_priority", columnNames = {"user_id", "priority"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPlatformPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}