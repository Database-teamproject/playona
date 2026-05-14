package com.playona.api.domain.auth;

import com.playona.api.domain.auth.entity.RefreshToken;
import com.playona.api.domain.auth.entity.RefreshTokenRepository;
import com.playona.api.global.common.ApiResponse;
import com.playona.api.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<?>> refresh(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refreshToken");

    RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 Refresh Token입니다."));

    if (token.isExpired()) {
      refreshTokenRepository.delete(token);
      throw new IllegalArgumentException("만료된 Refresh Token입니다.");
    }

    String newAccessToken = jwtProvider.generateToken(token.getUser().getUserUuid());
    return ResponseEntity.ok(ApiResponse.ok(Map.of("accessToken", newAccessToken)));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<?>> logout(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refreshToken");

    refreshTokenRepository.findByToken(refreshToken)
            .ifPresent(refreshTokenRepository::delete);

    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "로그아웃 되었습니다.")));
  }
}