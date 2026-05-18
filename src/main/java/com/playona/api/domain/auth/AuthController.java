package com.playona.api.domain.auth;

import com.playona.api.domain.auth.entity.RefreshToken;
import com.playona.api.domain.auth.entity.RefreshTokenRepository;
import com.playona.api.global.common.ApiResponse;
import com.playona.api.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;

  @Transactional
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<?>> refresh(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refreshToken");
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.badRequest().body(ApiResponse.fail("refreshToken은 필수입니다."));
    }
    // JWT 서명·만료·타입 검증을 DB 조회 전에 먼저 수행
    if (!jwtProvider.validateRefreshToken(refreshToken)) {
      return ResponseEntity.badRequest().body(ApiResponse.fail("유효하지 않은 Refresh Token입니다."));
    }

    RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 Refresh Token입니다."));

    if (token.isExpired()) {
      refreshTokenRepository.delete(token);
      throw new IllegalArgumentException("만료된 Refresh Token입니다.");
    }

    String userUuid = token.getUser().getUserUuid();
    String newAccessToken = jwtProvider.generateToken(userUuid);
    String newRefreshToken = jwtProvider.generateRefreshToken(userUuid);

    // Refresh Token Rotation: 기존 토큰 삭제 후 새 토큰 저장
    refreshTokenRepository.delete(token);
    refreshTokenRepository.save(new RefreshToken(
        token.getUser(),
        newRefreshToken,
        LocalDateTime.now().plusSeconds(jwtProvider.getRefreshExpiration() / 1000)
    ));

    return ResponseEntity.ok(ApiResponse.ok(Map.of(
        "accessToken", newAccessToken,
        "refreshToken", newRefreshToken
    )));
  }

  @Transactional
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<?>> logout(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refreshToken");
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenRepository.findByToken(refreshToken)
              .ifPresent(refreshTokenRepository::delete);
    }
    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "로그아웃 되었습니다.")));
  }
}