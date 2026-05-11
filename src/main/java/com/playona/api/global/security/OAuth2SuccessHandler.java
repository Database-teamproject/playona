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

import java.io.IOException;
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
  public void onAuthenticationSuccess(HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException {

    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

    String email;
    String name;
    String picture;

    if (oAuth2User.getAttribute("kakao_account") != null) {
      Long kakaoId = oAuth2User.getAttribute("id");
      email = "kakao_" + kakaoId + "@playona.com";
      var kakaoAccount = (java.util.Map) oAuth2User.getAttribute("kakao_account");
      var profile = (java.util.Map) kakaoAccount.get("profile");
      name = (String) profile.get("nickname");
      picture = (String) profile.get("profile_image_url");
    } else {
      email = oAuth2User.getAttribute("email");
      name = oAuth2User.getAttribute("name");
      picture = oAuth2User.getAttribute("picture");
    }

    User user = userRepository.findByEmail(email)
        .orElseGet(() -> userRepository.save(
            new User(UUID.randomUUID().toString(), email, name, picture)
        ));

    // Access Token 발급
    String accessToken = jwtProvider.generateToken(user.getUserUuid());

    // Refresh Token 발급 및 저장
    String refreshToken = jwtProvider.generateRefreshToken(user.getUserUuid());
    refreshTokenRepository.deleteByUserId(user.getId());
    refreshTokenRepository.save(new RefreshToken(
        user,
        refreshToken,
        LocalDateTime.now().plusSeconds(jwtProvider.getRefreshExpiration() / 1000)
    ));

    String targetUrl = redirectUrl + "?token=" + accessToken + "&refresh=" + refreshToken;
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}