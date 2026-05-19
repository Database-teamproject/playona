package com.playona.api.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * OAuth2 authorization request 를 서버 세션 대신 브라우저 쿠키에 저장.
 * Vercel 프록시처럼 요청이 여러 도메인을 거쳐도 state 가 유실되지 않음.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  private static final String COOKIE_NAME = "oauth2_auth_request";
  private static final int COOKIE_EXPIRE_SECONDS = 180;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return getCookieValue(request, COOKIE_NAME)
        .map(this::deserialize)
        .orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request, HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(response, COOKIE_NAME);
      return;
    }
    addCookie(response, COOKIE_NAME, serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
    deleteCookie(response, COOKIE_NAME);
    return authRequest;
  }

  private Optional<String> getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return Optional.empty();
    return Arrays.stream(cookies)
        .filter(c -> name.equals(c.getName()))
        .findFirst()
        .map(Cookie::getValue);
  }

  private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
    Cookie cookie = new Cookie(name, value);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(maxAge);
    response.addCookie(cookie);
  }

  private void deleteCookie(HttpServletResponse response, String name) {
    Cookie cookie = new Cookie(name, "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private String serialize(OAuth2AuthorizationRequest request) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(request);
      oos.close();
      return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException("OAuth2AuthorizationRequest 직렬화 실패", e);
    }
  }

  private OAuth2AuthorizationRequest deserialize(String value) {
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(value);
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
      return (OAuth2AuthorizationRequest) ois.readObject();
    } catch (Exception e) {
      return null;
    }
  }
}
