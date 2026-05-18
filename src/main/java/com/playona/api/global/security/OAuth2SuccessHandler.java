package com.playona.api.global.security;

import com.playona.api.domain.auth.entity.RefreshTokenRepository;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import com.playona.api.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  @Value("${oauth2.redirect-url}")
  private String redirectUrl;

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  @Transactional
  public void onAuthenticationSuccess(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException {

    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

    final String email;
    final String name;
    final String picture;

    if (oAuth2User.getAttribute("kakao_account") != null) {
      Long kakaoId = oAuth2User.getAttribute("id");
      email = "kakao_" + kakaoId + "@playona.com";

      Map<?, ?> kakaoAccount = oAuth2User.getAttribute("kakao_account");
      Map<?, ?> profile = (kakaoAccount != null) ? (Map<?, ?>) kakaoAccount.get("profile") : null;
      name = (profile != null && profile.get("nickname") != null)
          ? (String) profile.get("nickname")
          : "kakao_" + kakaoId;
      picture = (profile != null) ? (String) profile.get("profile_image_url") : null;
    } else {
      String rawEmail = oAuth2User.getAttribute("email");
      String rawName = oAuth2User.getAttribute("name");
      picture = oAuth2User.getAttribute("picture");

      // Google 계정에서 필수 필드 누락 시 fallback
      if (rawEmail == null || rawEmail.isBlank()) {
        throw new IllegalStateException("OAuth2 계정에서 이메일을 가져올 수 없습니다.");
      }
      email = rawEmail;
      name = (rawName == null || rawName.isBlank()) ? rawEmail.split("@")[0] : rawName;
    }

    User user = userRepository.findByEmail(email)
            .orElseGet(() -> userRepository.save(
                    new User(UUID.randomUUID().toString(), email, name, picture)
            ));

    // Access Token 발급
    String accessToken = jwtProvider.generateToken(user.getUserUuid());

    // Refresh Token 발급 및 저장 (Rotation)
    String refreshToken = jwtProvider.generateRefreshToken(user.getUserUuid());
    refreshTokenRepository.deleteByUserId(user.getId());
    refreshTokenRepository.save(new RefreshToken(
            user,
            refreshToken,
            LocalDateTime.now().plusSeconds(jwtProvider.getRefreshExpiration() / 1000)
    ));

    // 토큰을 URL fragment(#)로 전달 — query param은 서버 로그·브라우저 히스토리에 노출됨
    String targetUrl = redirectUrl + "#access=" + accessToken + "&refresh=" + refreshToken;
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}
