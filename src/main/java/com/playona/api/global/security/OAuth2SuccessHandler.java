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

    String email = oAuth2User.getAttribute("email");
    String name = oAuth2User.getAttribute("name");
    String picture = oAuth2User.getAttribute("picture");

    // DB에 유저 없으면 새로 저장 (회원가입), 있으면 그냥 조회 (로그인)
    User user = userRepository.findByEmail(email)
        .orElseGet(() -> userRepository.save(
            new User(UUID.randomUUID().toString(), email, name, picture)
        ));

    // JWT 토큰 발급
    String token = jwtProvider.generateToken(user.getUserUuid());

    // 프론트로 리다이렉트 (토큰 포함)
    String redirectUrl = "http://localhost:3000/auth/callback?token=" + token;
    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}