package com.playona.api.domain.auth.entity;

import com.playona.api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public RefreshToken(User user, String token, LocalDateTime expiresAt) {
    this.user = user;
    this.token = token;
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }
}