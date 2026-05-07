package com.playona.api.global.security;

import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException {

    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

    // 카카오/구글 분기 처리
    String email;
    String name;
    String picture;

    // 카카오는 id가 Long 타입으로 옴
    if (oAuth2User.getAttribute("kakao_account") != null) {
      // 카카오 로그인
      Long kakaoId = oAuth2User.getAttribute("id");
      email = "kakao_" + kakaoId + "@playona.com"; // 이메일 없으므로 임시 생성
      var kakaoAccount = (java.util.Map) oAuth2User.getAttribute("kakao_account");
      var profile = (java.util.Map) kakaoAccount.get("profile");
      name = (String) profile.get("nickname");
      picture = (String) profile.get("profile_image_url");
    } else {
      // 구글 로그인
      email = oAuth2User.getAttribute("email");
      name = oAuth2User.getAttribute("name");
      picture = oAuth2User.getAttribute("picture");
    }

    User user = userRepository.findByEmail(email)
        .orElseGet(() -> userRepository.save(
            new User(UUID.randomUUID().toString(), email, name, picture)
        ));

    String token = jwtProvider.generateToken(user.getUserUuid());
    String redirectUrl = "http://localhost:3000/auth/callback?token=" + token;
    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}