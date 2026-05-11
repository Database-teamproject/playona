package com.playona.api.domain.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {
  public User(String userUuid, String email, String nickname, String profileImageUrl) {
    this.userUuid = userUuid;
    this.email = email;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_uuid", unique = true, nullable = false)
  private String userUuid;

  @Column(unique = true, nullable = false)
  private String email;

  private String nickname;

  @Column(name = "profile_image_url")
  private String profileImageUrl;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}